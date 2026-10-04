using System.Diagnostics;
using System.IO;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text.Json;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using PhoneGamepad.Receiver.Engine;
using PhoneGamepad.Receiver.Network;
using PhoneGamepad.Receiver.Protocol;
using PhoneGamepad.Receiver.Safety;
using PhoneGamepad.Receiver.Security;
using PhoneGamepad.Receiver.Sinks;
using QRCoder;

namespace PhoneGamepad.Receiver;

public partial class MainWindow : Window
{
    private readonly KeyboardMouseSink _sink;
    private readonly FocusGuard _focusGuard;
    private readonly SafetyTriGuard _triGuard;
    private readonly LookDeltaProcessor _lookProcessor;
    private readonly KeyStateDiffEngine _keyDiffEngine;
    private readonly UdpListenerService _udpListener;
    private readonly byte[] _key;
    private readonly DispatcherTimer _telemetryTimer;

    private const int HotkeyId = 9001;
    private const uint ModAlt = 0x0001;
    private const uint ModControl = 0x0002;
    private const uint VkF12 = 0x7B;

    public MainWindow()
    {
        InitializeComponent();

        _key = DpapiStorage.GetOrCreateKey();
        _sink = new KeyboardMouseSink();
        _focusGuard = new FocusGuard();
        _triGuard = new SafetyTriGuard(_sink, _focusGuard);
        _lookProcessor = new LookDeltaProcessor();
        _keyDiffEngine = new KeyStateDiffEngine(30);

        _udpListener = new UdpListenerService(
            ProtocolConstants.DefaultPort,
            _key,
            _sink,
            _lookProcessor,
            _keyDiffEngine,
            _triGuard
        );

        _triGuard.ArmedChanged += OnArmedChanged;
        _triGuard.FocusChanged += OnFocusChanged;

        _telemetryTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromMilliseconds(100)
        };
        _telemetryTimer.Tick += OnTelemetryTick;

        Loaded += MainWindow_Loaded;
        Closing += MainWindow_Closing;
    }

    private void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        var helper = new WindowInteropHelper(this);
        var source = HwndSource.FromHwnd(helper.Handle);
        source?.AddHook(HwndHook);

        RegisterHotKey(helper.Handle, HotkeyId, ModControl | ModAlt, VkF12);

        RefreshProcessList();
        InitializePairingQrCode();
        _udpListener.Start();
        _telemetryTimer.Start();
    }

    private void InitializePairingQrCode()
    {
        try
        {
            var ips = GetLocalIPv4Addresses();
            string primaryIp = ips.FirstOrDefault() ?? "127.0.0.1";
            string hexKey = Convert.ToHexString(_key).ToLowerInvariant();

            TxtLocalIp.Text = $"IP: {primaryIp}";
            TxtPresharedKey.Text = $"Key: {hexKey[..8]}...{hexKey[^8..]}";
            TxtPresharedKey.ToolTip = hexKey;

            var payloadObj = new
            {
                ips = ips,
                port = ProtocolConstants.DefaultPort,
                key = hexKey,
                name = Environment.MachineName
            };
            string json = JsonSerializer.Serialize(payloadObj);

            using var qrGenerator = new QRCodeGenerator();
            using var qrCodeData = qrGenerator.CreateQrCode(json, QRCodeGenerator.ECCLevel.Q);
            var qrCode = new PngByteQRCode(qrCodeData);
            byte[] qrBytes = qrCode.GetGraphic(10);

            using var ms = new MemoryStream(qrBytes);
            var bitmap = new BitmapImage();
            bitmap.BeginInit();
            bitmap.CacheOption = BitmapCacheOption.OnLoad;
            bitmap.StreamSource = ms;
            bitmap.EndInit();
            bitmap.Freeze();

            ImgQrCode.Source = bitmap;
        }
        catch (Exception ex)
        {
            TxtLocalIp.Text = "QR Error: " + ex.Message;
        }
    }

    private static List<string> GetLocalIPv4Addresses()
    {
        var list = new List<string>();
        try
        {
            foreach (var netInterface in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (netInterface.OperationalStatus != OperationalStatus.Up ||
                    netInterface.NetworkInterfaceType == NetworkInterfaceType.Loopback)
                    continue;

                var ipProps = netInterface.GetIPProperties();
                foreach (var addr in ipProps.UnicastAddresses)
                {
                    if (addr.Address.AddressFamily == AddressFamily.InterNetwork)
                    {
                        string ipStr = addr.Address.ToString();
                        if (!ipStr.StartsWith("169.254.") && !ipStr.StartsWith("127."))
                        {
                            list.Add(ipStr);
                        }
                    }
                }
            }
        }
        catch { }
        if (list.Count == 0) list.Add("127.0.0.1");
        return list;
    }

    private void BtnCopyKey_Click(object sender, RoutedEventArgs e)
    {
        Clipboard.SetText(Convert.ToHexString(_key).ToLowerInvariant());
        BtnCopyKey.Content = "Copied!";
    }

    private void OnTelemetryTick(object? sender, EventArgs e)
    {
        TxtSessionId.Text = _udpListener.ActiveSessionId.HasValue
            ? $"Session ID: 0x{_udpListener.ActiveSessionId.Value:X8}"
            : "Session ID: None";

        TxtClientEndpoint.Text = _udpListener.ActiveClientEndpoint != null
            ? $"Client: {_udpListener.ActiveClientEndpoint}"
            : "Client: Not connected";

        TxtPacketCount.Text = $"Total Packets: {_udpListener.PacketCount}";
    }

    private IntPtr HwndHook(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        const int WmHotkey = 0x0312;
        if (msg == WmHotkey && wParam.ToInt32() == HotkeyId)
        {
            _triGuard.ToggleArmed();
            handled = true;
        }
        return IntPtr.Zero;
    }

    private void RefreshProcessList()
    {
        CmbProcessList.Items.Clear();
        CmbProcessList.Items.Add("[Any Window - Testing Only]");

        var processes = Process.GetProcesses()
            .Where(p => !string.IsNullOrEmpty(p.MainWindowTitle))
            .Select(p => p.ProcessName)
            .Distinct()
            .OrderBy(name => name);

        foreach (var p in processes)
        {
            CmbProcessList.Items.Add(p);
        }

        CmbProcessList.SelectedIndex = 0;
    }

    private void BtnRefreshProcesses_Click(object sender, RoutedEventArgs e) => RefreshProcessList();

    private void CmbProcessList_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (CmbProcessList.SelectedItem is string selected)
        {
            if (selected == "[Any Window - Testing Only]")
            {
                _focusGuard.AnyWindowMode = true;
                _focusGuard.TargetProcessName = null;
            }
            else
            {
                _focusGuard.AnyWindowMode = false;
                _focusGuard.TargetProcessName = selected;
            }
        }
    }

    private void BtnArmedToggle_Click(object sender, RoutedEventArgs e) => _triGuard.ToggleArmed();

    private void BtnNeutralizeAll_Click(object sender, RoutedEventArgs e) => _sink.NeutralizeAll();

    private void OnArmedChanged(bool isArmed)
    {
        Dispatcher.Invoke(() =>
        {
            if (isArmed)
            {
                BtnArmedToggle.Content = "DISARM RECEIVER";
                BtnArmedToggle.Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(22, 163, 74));
                TxtArmedStatus.Text = "ARMED";
                TxtArmedStatus.Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(74, 222, 128));
            }
            else
            {
                BtnArmedToggle.Content = "ARM RECEIVER";
                BtnArmedToggle.Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(220, 38, 38));
                TxtArmedStatus.Text = "DISARMED";
                TxtArmedStatus.Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(148, 163, 184));
            }
        });
    }

    private void OnFocusChanged(bool inFocus)
    {
        Dispatcher.Invoke(() =>
        {
            TxtFocusState.Text = inFocus ? "Target Focus: FOCUSED" : "Target Focus: LOST";
            TxtFocusState.Foreground = inFocus
                ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(74, 222, 128))
                : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(248, 113, 113));
        });
    }

    private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        _telemetryTimer.Stop();
        var helper = new WindowInteropHelper(this);
        UnregisterHotKey(helper.Handle, HotkeyId);
        _udpListener.Dispose();
        _sink.Dispose();
    }

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);
}
