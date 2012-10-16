using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using CefSharp;
using InTheHand.Net.Sockets;
using InTheHand.Net.Bluetooth;
using Json;
using System.IO;

namespace BTWebKitTest
{
    public partial class Main : Form
    {
        CefSharp.WinForms.WebView view = null;

        public class BTInterface
        {
            private Main parent;

            class BTResponse
            {
                public ICollection<BTRadio> radios {get; set;}
            }

            class BTRadio 
            {
                public String address { get; set; }
                public String name { get; set; }
                public ICollection<BTRadio> devices { get; set; }
            }

            public void init(Main main)
            {
                this.parent = main;
            }

            class PluginInfo
            {
                public string file { get; set; }
            }

            class PluginsResponse
            {
                public ICollection<PluginInfo> plugins { get; set; }
            }

            public void enumeratePlugins(String handler) 
            {
                try
                {
                    PluginsResponse response = new PluginsResponse();
                    response.plugins = new List<PluginInfo>();
                    var files = Directory.EnumerateFiles("client\\plugins", "*.js");
                    foreach (string fileName in files)
                    {
                        var file = new FileInfo(fileName);
//                        Console.Out.WriteLine("File: " + fileName+", "+file.Name);
                        var info = new PluginInfo();
                        info.file = file.Name;
                        response.plugins.Add(info);
                    }
                    parent.events.call(handler, true, null, JsonParser.Serialize<PluginsResponse>(response));
                }
                catch (Exception)
                {
                    parent.events.call(handler, true, "Error loading plugins", null);
                }
            }

            public void enumerateDevices(String handler)
            {
                BTResponse devs = new BTResponse();
                devs.radios = new List<BTRadio>();
                foreach (BluetoothRadio radio in BluetoothRadio.AllRadios)
                {
                    BTRadio r = new BTRadio();
                    r.address = radio.LocalAddress.ToString();
                    r.name = radio.Name;
                    devs.radios.Add(r);
                    r.devices = new List<BTRadio>();
                    InTheHand.Net.Sockets.BluetoothDeviceInfo[] infos = radio.StackFactory.CreateBluetoothClient().DiscoverDevices(99, true, true, false);
                    foreach (InTheHand.Net.Sockets.BluetoothDeviceInfo info in infos)
                    {
                        Console.Out.WriteLine("Info: " + info.DeviceAddress+", "+info.DeviceName);
                        BTRadio dev = new BTRadio();
                        dev.address = info.DeviceAddress.ToString();
                        dev.name = info.DeviceName;
                        r.devices.Add(dev);
                    }
                }
                parent.events.call(handler, true, null, JsonParser.Serialize<BTResponse>(devs));
            }

            public bool isSupported()
            {
//                Console.Out.WriteLine("BT: " + BluetoothRadio.AllRadios.Length);
                if (0 == BluetoothRadio.AllRadios.Length)
                {
                    return false;
                }
                BluetoothRadio radio = BluetoothRadio.PrimaryRadio;
//                Console.Out.WriteLine("BT: " + radio.HardwareStatus+", "+radio.Name+", "+radio.SoftwareManufacturer+", "+radio.LocalAddress.ToString());
                return BluetoothRadio.IsSupported;
            }

        }

        public class EventInterface
        {
            private Main parent;
            
            public String method()
            {
                return "_event";
            }

            public void init(Main main)
            {
                this.parent = main;
            }

            public void call(String handler, bool last, String error, String obj)
            {
                Console.Out.WriteLine("call: " + obj);
                parent.view.EvaluateScript(handler + "(" + (last ? "true" : "false") + ", " + 
                    (null == error ? "null" : "'" + error + "'") + 
                    ", "+(null == obj? "null": obj)+");");
            }

        }

        BTInterface bt = new BTInterface();
        EventInterface events = new EventInterface();

        public Main()
        {
            InitializeComponent();
            CefSharp.Settings settings = new Settings();
//            settings.AutoDetectProxySettings = true;
            settings.CachePath = "cache";
            settings.LogFile = "Kostya.log";
            settings.LogSeverity = LogSeverity.Warning;
            CEF.Initialize(settings);
            CefSharp.BrowserSettings bsettings = new BrowserSettings();
            bsettings.WebSecurityDisabled = true;
            bsettings.FileAccessFromFileUrlsAllowed = true;
            bsettings.XssAuditorEnabled = false;
            bsettings.DeveloperToolsDisabled = false;
            bsettings.UniversalAccessFromFileUrlsAllowed = true;
            bsettings.JavaScriptDisabled = false;
            view = new CefSharp.WinForms.WebView("file:///./client/start.html", bsettings);
            bt.init(this);
            view.RegisterJsObject("bt", bt);
            events.init(this);
            view.RegisterJsObject("events", events);
            view.Dock = DockStyle.Fill;
            webPanelParent.Controls.Add(view, 0, 1);
        }

        private void showDevTools_Click(object sender, EventArgs e)
        {
            view.ShowDevTools();
        }

        private void Reload_Click(object sender, EventArgs e)
        {
            view.Reload(true);
        }
    }
}
