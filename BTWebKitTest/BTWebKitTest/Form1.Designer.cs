namespace BTWebKitTest
{
    partial class Main
    {
        /// <summary>
        /// Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        /// Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        /// <summary>
        /// Required method for Designer support - do not modify
        /// the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            this.components = new System.ComponentModel.Container();
            System.ComponentModel.ComponentResourceManager resources = new System.ComponentModel.ComponentResourceManager(typeof(Main));
            this.webPanelParent = new System.Windows.Forms.TableLayoutPanel();
            this.toolStrip1 = new System.Windows.Forms.ToolStrip();
            this.showDevTools = new System.Windows.Forms.ToolStripButton();
            this.Reload = new System.Windows.Forms.ToolStripButton();
            this.tray = new System.Windows.Forms.NotifyIcon(this.components);
            this.webPanelParent.SuspendLayout();
            this.toolStrip1.SuspendLayout();
            this.SuspendLayout();
            // 
            // webPanelParent
            // 
            this.webPanelParent.ColumnCount = 1;
            this.webPanelParent.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 100F));
            this.webPanelParent.Controls.Add(this.toolStrip1, 0, 0);
            this.webPanelParent.Dock = System.Windows.Forms.DockStyle.Fill;
            this.webPanelParent.Location = new System.Drawing.Point(0, 0);
            this.webPanelParent.Name = "webPanelParent";
            this.webPanelParent.RowCount = 2;
            this.webPanelParent.RowStyles.Add(new System.Windows.Forms.RowStyle());
            this.webPanelParent.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 100F));
            this.webPanelParent.Size = new System.Drawing.Size(1008, 677);
            this.webPanelParent.TabIndex = 0;
            // 
            // toolStrip1
            // 
            this.toolStrip1.Items.AddRange(new System.Windows.Forms.ToolStripItem[] {
            this.showDevTools,
            this.Reload});
            this.toolStrip1.Location = new System.Drawing.Point(0, 0);
            this.toolStrip1.Name = "toolStrip1";
            this.toolStrip1.Size = new System.Drawing.Size(1008, 25);
            this.toolStrip1.TabIndex = 0;
            this.toolStrip1.Text = "toolStrip1";
            // 
            // showDevTools
            // 
            this.showDevTools.Image = ((System.Drawing.Image)(resources.GetObject("showDevTools.Image")));
            this.showDevTools.ImageTransparentColor = System.Drawing.Color.Magenta;
            this.showDevTools.Name = "showDevTools";
            this.showDevTools.Size = new System.Drawing.Size(83, 22);
            this.showDevTools.Text = "Dev tools";
            this.showDevTools.Click += new System.EventHandler(this.showDevTools_Click);
            // 
            // Reload
            // 
            this.Reload.Enabled = false;
            this.Reload.Image = ((System.Drawing.Image)(resources.GetObject("Reload.Image")));
            this.Reload.ImageTransparentColor = System.Drawing.Color.Magenta;
            this.Reload.Name = "Reload";
            this.Reload.Size = new System.Drawing.Size(67, 22);
            this.Reload.Text = "Reload";
            this.Reload.Click += new System.EventHandler(this.Reload_Click);
            // 
            // tray
            // 
            this.tray.Icon = ((System.Drawing.Icon)(resources.GetObject("tray.Icon")));
            this.tray.Text = "Foxtrot7";
            this.tray.Visible = true;
            // 
            // Main
            // 
            this.AutoScaleDimensions = new System.Drawing.SizeF(6F, 12F);
            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            this.ClientSize = new System.Drawing.Size(1008, 677);
            this.Controls.Add(this.webPanelParent);
            this.Icon = ((System.Drawing.Icon)(resources.GetObject("$this.Icon")));
            this.Name = "Main";
            this.StartPosition = System.Windows.Forms.FormStartPosition.CenterScreen;
            this.Text = "Bluetooth Manager";
            this.webPanelParent.ResumeLayout(false);
            this.webPanelParent.PerformLayout();
            this.toolStrip1.ResumeLayout(false);
            this.toolStrip1.PerformLayout();
            this.ResumeLayout(false);

        }

        #endregion

        private System.Windows.Forms.TableLayoutPanel webPanelParent;
        private System.Windows.Forms.ToolStrip toolStrip1;
        private System.Windows.Forms.ToolStripButton showDevTools;
        private System.Windows.Forms.ToolStripButton Reload;
        private System.Windows.Forms.NotifyIcon tray;

    }
}

