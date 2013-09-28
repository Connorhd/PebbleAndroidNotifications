package uk.co.connorhd.android.pebblenotifications;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}

	public void buttonClicked(View v) {
		if (v.getId() == R.id.btnCreateNotify) {
			NotificationManager nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			Notification.Builder ncomp = new Notification.Builder(this);
			ncomp.setContentTitle("Test notification");
			ncomp.setContentText("Hello, world!");
			ncomp.setSmallIcon(R.drawable.ic_launcher);
			ncomp.setAutoCancel(true);
			nManager.notify((int) System.currentTimeMillis(), ncomp.build());
		} else if (v.getId() == R.id.btnAddPermission) {
			Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
			startActivity(intent);
		} else if (v.getId() == R.id.btnInstallApp) {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://connorhd.co.uk/pebble/notifications/watchapp.pbw"));
			startActivity(intent);
		}
	}
}
