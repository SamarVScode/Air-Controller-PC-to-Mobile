using System.Windows;

namespace PhoneGamepad.Receiver;

public partial class App : Application
{
    private static Mutex? _instanceMutex;

    protected override void OnStartup(StartupEventArgs e)
    {
        const string mutexName = "Global\\PhoneAsGamepad_Receiver_SingleInstance";
        _instanceMutex = new Mutex(true, mutexName, out bool createdNew);

        if (!createdNew)
        {
            MessageBox.Show("Another instance of Phone-as-Gamepad Receiver is already running.", "Instance Running", MessageBoxButton.OK, MessageBoxImage.Warning);
            Shutdown();
            return;
        }

        base.OnStartup(e);
    }
}
