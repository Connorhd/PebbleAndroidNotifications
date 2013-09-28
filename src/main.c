#include "pebble_os.h"
#include "pebble_app.h"
#include "pebble_fonts.h"

#define MY_UUID { 0xF0, 0xD3, 0x40, 0x3D, 0x9C, 0xEC, 0x41, 0x01, 0x85, 0x02, 0x2A, 0x80, 0x1F, 0xE2, 0x47, 0x61 }
PBL_APP_INFO(MY_UUID,
             "Notifications", "Connor Dunn",
             1, 0, /* App version */
             RESOURCE_ID_IMAGE_ICON,
             APP_INFO_STANDARD_APP);

#define MAX_NOTIFICATIONS 25

Window window;
Layer layer;
ActionBarLayer action_bar;

BmpContainer button_up;
BmpContainer button_down;
BmpContainer button_cross;

typedef struct Notification {
	uint8_t icon[384];
	char title[20];
	char details[60];
} Notification;

Notification notifications[MAX_NOTIFICATIONS];

int8_t loadingNotification = 0;
int8_t atNotification = -1;

void update_layer_callback(Layer *me, GContext *ctx) {
	graphics_context_set_text_color(ctx, GColorBlack);
	
	if (atNotification < 0) {
		graphics_text_draw(ctx,
		     atNotification == -2 ? "No notifications" : "Loading...",
		     fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD),
		     GRect(5, 53, 144 - ACTION_BAR_WIDTH - 10, 60),
		     GTextOverflowModeTrailingEllipsis,
		     GTextAlignmentCenter,
		     NULL);
		
		action_bar_layer_clear_icon(&action_bar, BUTTON_ID_UP);
		action_bar_layer_clear_icon(&action_bar, BUTTON_ID_SELECT);
		action_bar_layer_clear_icon(&action_bar, BUTTON_ID_DOWN);
	} else {
		GBitmap bitmap = {
			.addr = &notifications[atNotification].icon,
			.bounds = GRect(0, 0, 48, 48),
			.info_flags = 0x1000,
			.row_size_bytes = 8
		};
		graphics_context_set_compositing_mode(ctx, GCompOpAssignInverted);
		graphics_draw_bitmap_in_rect(ctx, &bitmap, GRect(38, 5, 48, 48));
		
		graphics_text_draw(ctx,
						   notifications[atNotification].title,
						   fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD),
						   GRect(5, 53, 144 - ACTION_BAR_WIDTH - 10, 24),
						   GTextOverflowModeTrailingEllipsis,
						   GTextAlignmentCenter,
						   NULL);
		graphics_text_draw(ctx,
						   notifications[atNotification].details,
						   fonts_get_system_font(FONT_KEY_GOTHIC_18),
						   GRect(5, 82, 144 - ACTION_BAR_WIDTH - 10, 81),
						   GTextOverflowModeTrailingEllipsis,
						   GTextAlignmentCenter,
						   NULL);
		
		if (atNotification == 0) {
			action_bar_layer_clear_icon(&action_bar, BUTTON_ID_UP);
		} else {
				action_bar_layer_set_icon(&action_bar, BUTTON_ID_UP, &button_up.bmp);
		}
		action_bar_layer_set_icon(&action_bar, BUTTON_ID_SELECT, &button_cross.bmp);
		if (atNotification == loadingNotification) {
			action_bar_layer_clear_icon(&action_bar, BUTTON_ID_DOWN);
		} else {
			action_bar_layer_set_icon(&action_bar, BUTTON_ID_DOWN, &button_down.bmp);
		}
	}
}


void my_next_click_handler(ClickRecognizerRef recognizer, Window *window) {
	if (atNotification > -1 && atNotification < loadingNotification) {
		atNotification++;
		layer_mark_dirty(&layer);
	}
}
void my_previous_click_handler(ClickRecognizerRef recognizer, Window *window) {
	if (atNotification > 0) {
		atNotification--;
		layer_mark_dirty(&layer);
	}
}
void my_select_click_handler(ClickRecognizerRef recognizer, Window *window) {
	if (atNotification > -1) {
		DictionaryIterator *dict;
		if (app_message_out_get(&dict) == APP_MSG_OK) {
			dict_write_int8(dict, 1, (int8_t)atNotification);
			app_message_out_send();
			app_message_out_release();
		}
	}
}

void click_config_provider(ClickConfig **config, void *context) {
	config[BUTTON_ID_DOWN]->click.handler = (ClickHandler) my_next_click_handler;
	config[BUTTON_ID_UP]->click.handler = (ClickHandler) my_previous_click_handler;
	config[BUTTON_ID_SELECT]->click.handler = (ClickHandler) my_select_click_handler;
}

void ask_for_data() {
	DictionaryIterator *dict;
	if (app_message_out_get(&dict) == APP_MSG_OK) {
		dict_write_uint8(dict, 0, 1);
		app_message_out_send();
		app_message_out_release();
	}
}

void handle_init(AppContextRef ctx) {
	// Load resources
	resource_init_current_app(&APP_RESOURCES);
	bmp_init_container(RESOURCE_ID_IMAGE_UP, &button_up);
	bmp_init_container(RESOURCE_ID_IMAGE_DOWN, &button_down);
	bmp_init_container(RESOURCE_ID_IMAGE_CROSS, &button_cross);
	
	// Setup the window
	window_init(&window, "Notifications");
	window_set_fullscreen(&window, true);
	window_stack_push(&window, true /* Animated */);
	
	// Setup the layer that will display the text
	layer_init(&layer, (GRect){ .origin = GPointZero, .size = window.layer.frame.size });
	layer.update_proc = update_layer_callback;
	layer_add_child(&window.layer, &layer);
	
	action_bar_layer_init(&action_bar);
	// Associate the action bar with the window:
	action_bar_layer_add_to_window(&action_bar, &window);
	// Set the click config provider:
	action_bar_layer_set_click_config_provider(&action_bar, click_config_provider);

	// Draw layer
	layer_mark_dirty(&layer);
	
	ask_for_data();
}

void handle_deinit(AppContextRef ctx) {
  	bmp_deinit_container(&button_up);
	bmp_deinit_container(&button_down);
	bmp_deinit_container(&button_cross);
}

void my_out_sent_handler(DictionaryIterator *sent, void *context) {

}
void my_out_fail_handler(DictionaryIterator *failed, AppMessageResult reason, void *context) {

}
void my_in_rcv_handler(DictionaryIterator *received, void *context) {
	Tuple* cmd_tuple = dict_find(received, 500);
	
	if (cmd_tuple != NULL) {
		ask_for_data();
	}
	
	cmd_tuple = dict_find(received, 700);
	
	if (cmd_tuple != NULL) {
		atNotification = -2;
	}
	
	cmd_tuple = dict_find(received, 300);
	
	if (cmd_tuple != NULL) {
		loadingNotification = cmd_tuple->value->int8;
		if (loadingNotification == 0) {
			atNotification = 0;
			vibes_short_pulse();
		}
	}
	
	cmd_tuple = dict_find(received, 0);
	
	if (cmd_tuple != NULL) {
		for (int i = 0; i < 116; i++) {
			notifications[loadingNotification].icon[i + (i / 6) * 2] = cmd_tuple->value->data[i];
		}
	}

	cmd_tuple = dict_find(received, 1);
	
	if (cmd_tuple != NULL) {
		for (int i = 116; i < 116*2; i++) {
			notifications[loadingNotification].icon[i + (i / 6) * 2] = cmd_tuple->value->data[i-116];
		}
	}
	
	cmd_tuple = dict_find(received, 2);
	
	if (cmd_tuple != NULL) {
		for (int i = 116*2; i < 116*2+56; i++) {
			notifications[loadingNotification].icon[i + (i / 6) * 2] = cmd_tuple->value->data[i-116*2];
		}
	}
	
		
	cmd_tuple = dict_find(received, 200);
	
	if (cmd_tuple != NULL) {
		strcpy(&notifications[loadingNotification].title[0], cmd_tuple->value->cstring);
	}	
	cmd_tuple = dict_find(received, 100);
	
	if (cmd_tuple != NULL) {
		strcpy(&notifications[loadingNotification].details[0], cmd_tuple->value->cstring);
	}
	
  	layer_mark_dirty(&layer);
}
void my_in_drp_handler(void *context, AppMessageResult reason) {

}

void pbl_main(void *params) {
	PebbleAppHandlers handlers = {
		.init_handler = &handle_init,
		.deinit_handler = &handle_deinit,
		.messaging_info = {
			.buffer_sizes = {
				.inbound = 128, // inbound buffer size in bytes
				.outbound = 32, // outbound buffer size in bytes
			},
				.default_callbacks.callbacks = {
				.out_sent = my_out_sent_handler,
				.out_failed = my_out_fail_handler,
				.in_received = my_in_rcv_handler,
				.in_dropped = my_in_drp_handler,
			}
		}
	};
	app_event_loop(params, &handlers);
}
