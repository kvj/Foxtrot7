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
using InTheHand.Net;
using InTheHand.Net.Bluetooth;
using Json;
using System.IO;

// GUID: {EBB4AF8E-E8F1-46A2-9B52-9980FD3CE6DC}

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

            class BTConnectInfo
            {
                public String handler;
                public BluetoothListener listener;
            }

            void listenCallback(IAsyncResult result) {
                log(this, "Incoming connection: " + result.IsCompleted+", "+result.AsyncState);
                BTConnectInfo ctx = (BTConnectInfo)result.AsyncState;
                try
                {
                    log(this, "Pending: " + ctx.listener.Pending());
                    var client = ctx.listener.EndAcceptBluetoothClient(result);
                    log(this, "Have client: " + client.RemoteMachineName);
                    var stream = client.GetStream();
                    log(this, "Have stream: " + stream);
                    var reader = new BinaryReader(stream, System.Text.Encoding.UTF8);
                    string data = null;

                    try
                    {
                        while ((data = reader.ReadString()) != null)
                        {
                            log(this, "Got data: " + data);
                            if ("" == data)
                            {
                                log(this, "EOS detected");
                                break;
                            }
                            object jsResult = parent.events.call(ctx.handler, false, null, "'" + client.RemoteEndPoint.Address.ToString() + "'", data);
                            int res = 0;
                            if (null != jsResult && jsResult is Int32)
                            {
                                res = (int)jsResult;
                            }
                            log(this, "Writing response: " + res);
                            stream.WriteByte((byte)res);
                            stream.Flush();
                        }
                    }
                    catch (Exception e) { }
                    client.Close();
                    ctx.listener.BeginAcceptBluetoothClient(new AsyncCallback(listenCallback), ctx);
                    log(this, "Remote connection finished");
                }
                catch (Exception e)
                {
                    log(this, "Error: " + e);
                    //parent.events.call(ctx.handler, true, "Error opening port", null);
                }
            }

            class BTSendContext
            {
                public string data;
                public Guid guid;
                public BluetoothAddress addr;
                public BluetoothClient client;
                public string handler;
            }

            private void onServiceRecordParse(IAsyncResult result)
            {
                log(this, "Parse: " + result.AsyncState + ", " + result.IsCompleted);
                BTSendContext ctx = (BTSendContext)result.AsyncState;
                try
                {
                    log(this, "Found, connecting: " + ctx.guid);
                    ctx.client.Connect(ctx.addr, ctx.guid);
                    log(this, "Connected");
                    var stream = ctx.client.GetStream();
                    log(this, "Connect: " + ctx.addr + " done: "+ctx.data.Length);
//                    byte[] b1 = System.Text.Encoding.UTF8.GetBytes(ctx.data);
                    BinaryWriter writer = new BinaryWriter(stream, System.Text.Encoding.UTF8);
                    writer.Write(ctx.data);
                    int res = stream.ReadByte();
                    writer.Write(0); // EOS
                    stream.Close();
                    log(this, "Closed: " + res + " done");
                    parent.events.call(ctx.handler, true, null, ""+res);
                }
                catch (Exception e)
                {
                    log(this, "Error: " + e);
                    parent.events.call(ctx.handler, true, "Error opening port", null);
                }
            }

            public void send(String device, String uuid, String remote, String data, String handler) 
            {
                try
                {
                    foreach (BluetoothRadio radio in BluetoothRadio.AllRadios)
                    {
                        if (radio.LocalAddress.ToString().Equals(device))
                        {
                            // Found
                            var client = radio.StackFactory.CreateBluetoothClient();
                            BluetoothAddress addr = null;
                            InTheHand.Net.Sockets.BluetoothDeviceInfo[] infos = client.DiscoverDevices(99, true, true, false);
                            var guid = new Guid(uuid);
                            foreach (var info in infos)
                            {
                                if (info.DeviceAddress.ToString().Equals(remote))
                                {
                                    addr = info.DeviceAddress;
                                    var ctx = new BTSendContext();
                                    ctx.addr = addr;
                                    ctx.data = data;
                                    ctx.guid = guid;
                                    ctx.handler = handler;
                                    ctx.client = client;
                                    info.BeginGetServiceRecords(guid, new AsyncCallback(onServiceRecordParse), ctx);
                                    return;
                                }
                            }
                            if (null == addr)
                            {
                                log(this, "Device wasn't found: "+remote);
                                parent.events.call(handler, true, "Device not found", null);
                                return;
                            }
                            return;
                        }
                    }
                    parent.events.call(handler, true, "Adapter not found", null);
                }
                catch (Exception e)
                {
                    log(this, "Error: " + e);
                    parent.events.call(handler, true, "Error opening port", null);
                }
            }

            public void listen(String device, String uuid, String handler, String connectHandler)
            {
                try
                {
                    foreach (BluetoothRadio radio in BluetoothRadio.AllRadios)
                    {
                        if (radio.LocalAddress.ToString().Equals(device))
                        {
                            // Found
                            var guid = new Guid(uuid);
                            log(this, "Found: " + radio.Name+", opening: "+guid);
                            BluetoothListener listener = radio.StackFactory.CreateBluetoothListener(guid);
                            var info = new BTConnectInfo();
                            info.handler = connectHandler;
                            info.listener = listener;
                            listener.Start();
                            listener.BeginAcceptBluetoothClient(new AsyncCallback(listenCallback), info);
                            parent.events.call(handler, true, null, "true");
                            log(this, "Open: " + radio.Name + " done");
                            return;
                        }
                    }
                    parent.events.call(handler, true, "Adapter not found", null);
                }
                catch (Exception e)
                {
                    log(this, "Error: " + e);
                    parent.events.call(handler, true, "Error opening port", null);
                }
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

            public object call(String handler, bool last, String error, params String[] obj)
            {
                string js = handler + "(" + (last ? "true" : "false") + ", " +
                    (null == error ? "null" : "'" + error + "'");
                if (null != obj)
                {
                    foreach (var one in obj)
                    {
                        js += ", " + (null == one ? "null" : one);
                    }                    
                }
                object result = parent.view.EvaluateScript( js+");");
                if (null != result)
                {
                    log(this, "Output result: "+result.GetType());
                }
                return result;
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
            bsettings.ApplicationCacheDisabled = false;
            bsettings.DatabasesDisabled = false;
            bsettings.LocalStorageDisabled = false;
            bsettings.PageCacheDisabled = false;
            bsettings.FileAccessFromFileUrlsAllowed = true;
            bsettings.XssAuditorEnabled = false;
            bsettings.DeveloperToolsDisabled = false;
            bsettings.UniversalAccessFromFileUrlsAllowed = true;
            bsettings.JavaScriptDisabled = false;
            bsettings.LocalStorageDisabled = false;
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

        public static void log(object call, string message)
        {
            Console.WriteLine("" + call.GetType().Name + ": " + message);
        }
    }
}
