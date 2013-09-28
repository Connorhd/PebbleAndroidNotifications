package uk.co.connorhd.android.pebblenotifications;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import android.app.Notification;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.util.Log;
import android.widget.RemoteViews;

import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleAckReceiver;
import com.getpebble.android.kit.PebbleKit.PebbleDataReceiver;
import com.getpebble.android.kit.PebbleKit.PebbleNackReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

public class NotificationService extends NotificationListenerService {
	private final UUID appUUID = UUID.fromString("F0D3403D-9CEC-4101-8502-2A801FE24761");

	private LinkedList<PebbleDictionary> outgoing = new LinkedList<PebbleDictionary>();
	private boolean flushing = false;
	private HashMap<Integer, StatusBarNotification> sentNotifications = new HashMap<Integer, StatusBarNotification>();

	private PebbleAckReceiver pAck;
	private PebbleNackReceiver pNack;
	private PebbleDataReceiver pData;

	private void flushOutgoing() {
		if (!PebbleKit.isWatchConnected(getApplicationContext())) {
			outgoing.remove();
		}
		if (outgoing.size() > 0 && !flushing) {
			flushing = true;
			PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), appUUID, outgoing.get(0), 174);
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();

		pAck = new PebbleAckReceiver(appUUID) {
			@Override
			public void receiveAck(Context context, int transactionId) {
				if (transactionId == 174) {
					outgoing.remove();
					flushing = false;
					flushOutgoing();
				}
			}
		};
		IntentFilter filter = new IntentFilter();
		filter.addAction(Constants.INTENT_APP_RECEIVE_ACK);
		registerReceiver(pAck, filter);

		pNack = new PebbleNackReceiver(appUUID) {
			@Override
			public void receiveNack(Context context, int transactionId) {
				if (transactionId == 174) {
					// Give up sending data - TODO: something better?
					outgoing.clear();
					flushing = false;
				}

			}
		};
		filter = new IntentFilter();
		filter.addAction(Constants.INTENT_APP_RECEIVE_NACK);
		registerReceiver(pNack, filter);

		pData = new PebbleDataReceiver(appUUID) {
			@SuppressWarnings("unchecked")
			@Override
			public void receiveData(Context context, int transactionId, PebbleDictionary data) {
				PebbleKit.sendAckToPebble(context, transactionId);
				if (data.contains(0)) {
					// Watch wants data
					int notifId = 0;
					List<StatusBarNotification> sbns = Arrays.asList(getActiveNotifications());
					Collections.reverse(sbns);
					for (StatusBarNotification sbn : sbns) {
						if (notifId == 25) {
							// Can't send that many notifications
							return;
						}
						if (sbn.isOngoing() || sbn.getNotification().priority < Notification.PRIORITY_DEFAULT) {
							// Ignore some notification types
							continue;
						}
						
						// Get title
						final PackageManager pm = getApplicationContext().getPackageManager();
						ApplicationInfo ai;
						try {
							ai = pm.getApplicationInfo(sbn.getPackageName(), 0);
						} catch (final NameNotFoundException e) {
							ai = null;
						}
						final String title = (String) (ai != null ? pm.getApplicationLabel(ai) : "(unknown)");
						
						// Get details
						String details = "Failed to find additional notification text.";
						
						if (sbn.getNotification().tickerText != null) {
							details = sbn.getNotification().tickerText.toString();
						} else if (sbn.getNotification().tickerView != null) {
							RemoteViews rv = sbn.getNotification().tickerView;

							try {
								details = "";
								for (Field field : rv.getClass().getDeclaredFields()) {
									field.setAccessible(true);

									if (field.getName().equals("mActions")) {
										ArrayList<Object> things = (ArrayList<Object>) field.get(rv);
										Log.w("ABC", things.toString());
										for (Object object : things) {
											for (Field innerField : object.getClass().getDeclaredFields()) {
												innerField.setAccessible(true);
												if (innerField.getName().equals("value")) {
													Object innerObj = innerField.get(object);
													if (innerObj instanceof String || innerObj instanceof SpannableString) {
														if (details.length() > 0) {
															details += " - ";
														}
														try {
															details += innerField.get(object).toString();
														} catch (Exception e) {}
													}
												}

											}
										}
									}
								}
							} catch (Exception e) {
								details = "Failed to find additional notification text.";
							}
						}
						
						PebbleDictionary dict = new PebbleDictionary();
						dict.addString(200, title.substring(0, Math.min(title.length(), 19)));
						dict.addString(100, details.substring(0, Math.min(details.length(), 59)));
						sentNotifications.put(notifId, sbn);
						dict.addInt8(300, (byte) notifId++);
						outgoing.add(dict);
						
						// Get icon
						
						int iconId = sbn.getNotification().icon;
						Resources res = null;
						try {
							res = getPackageManager().getResourcesForApplication(sbn.getPackageName());
						} catch (NameNotFoundException e) {
						}
						if (res != null) {
							Bitmap icon = BitmapFactory.decodeResource(res, iconId);
							icon = Bitmap.createScaledBitmap(icon, 48, 48, false);
	
							byte[] bitmap = new byte[116];
	
							int atByte = 0;
							int atKey = 0;
							StringBuilder bin = new StringBuilder();
	
							boolean grayscale = true;
							for (int y = 0; y < 48; y++) {
								for (int x = 0; x < 48; x++) {
									int c = icon.getPixel(x, y);
									if ((c & 0x000000FF) != (c >> 8 & 0x000000FF) || (c >> 8 & 0x000000FF) != (c >> 16 & 0x000000FF)) {
										grayscale = false;
									}
								}
							}
	
							for (int y = 0; y < 48; y++) {
								for (int x = 0; x < 48; x++) {
									int c = icon.getPixel(x, y);
									int averageColor = ((c & 0x000000FF) + (c >> 8 & 0x000000FF) + (c >> 16 & 0x000000FF)) / 3;
									int opacity = ((c >> 24) & 0x000000FF);
	
									// Less than 50% opacity or (if a colour icon)
									// very light colours are "white"
									if (opacity < 127 || (!grayscale && averageColor > 225)) {
										bin.append("0");
									} else {
										bin.append("1");
									}
									if (bin.length() == 8) {
										bin.reverse();
										bitmap[atByte++] = (byte) Integer.parseInt(bin.toString(), 2);
										bin = new StringBuilder();
									}
									if (atByte == 116) {
										dict = new PebbleDictionary();
										dict.addBytes(atKey++, bitmap);
										outgoing.add(dict);
										atByte = 0;
										bitmap = new byte[116];
									}
								}
	
							}
							dict = new PebbleDictionary();
							dict.addBytes(atKey, bitmap);
							outgoing.add(dict);
						}
					}
					
					if (notifId == 0) {
						// Tell the watch no notifications
						PebbleDictionary dict = new PebbleDictionary();
						dict.addInt8(700, (byte) 1);
						outgoing.add(dict);
					}
					flushOutgoing();
				} else if (data.contains(1)) {
					// Dismiss notification
					int notifId = data.getInteger(1).intValue();
					StatusBarNotification notif = sentNotifications.get(notifId);
					if (notif != null) {
						cancelNotification(notif.getPackageName(), notif.getTag(), notif.getId());
					}
				}
			}
		};

		filter = new IntentFilter();
		filter.addAction(Constants.INTENT_APP_RECEIVE);
		registerReceiver(pData, filter);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(pAck);
		unregisterReceiver(pNack);
		unregisterReceiver(pData);
	}

	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		if (!sbn.isOngoing() && sbn.getNotification().priority >= Notification.PRIORITY_DEFAULT) {
			// Start if not running
			PebbleKit.startAppOnPebble(getApplicationContext(), appUUID);

			// If running notify that new notifications exist
			PebbleDictionary dict = new PebbleDictionary();
			dict.addInt8(500, (byte) 0);
			outgoing.add(dict);
			flushOutgoing();
		}
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		// If running notify that new notifications exist
		PebbleDictionary dict = new PebbleDictionary();
		dict.addInt8(500, (byte) 0);
		outgoing.add(dict);
		flushOutgoing();
	}
}
