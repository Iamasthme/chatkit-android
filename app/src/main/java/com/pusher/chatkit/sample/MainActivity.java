package com.pusher.chatkit.sample;

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.pusher.chatkit.ChatManager;
import com.pusher.chatkit.ChatkitTokenProvider;
import com.pusher.chatkit.CurrentUser;
import com.pusher.chatkit.CurrentUserListener;
import com.pusher.chatkit.ErrorListener;
import com.pusher.chatkit.RemovedFromRoomListener;
import com.pusher.chatkit.Room;
import com.pusher.chatkit.RoomListener;
import com.pusher.chatkit.UserSubscriptionListeners;
import com.pusher.platform.logger.LogLevel;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import elements.Error;
import okhttp3.OkHttpClient;
import timber.log.Timber;

public class MainActivity extends Activity {

    public static final String TAG = "Chatkit Sample";
    public static final String INSTANCE_ID = "v1:us1:c090a50e-3e0e-4d05-96b0-a967ee4717ad";

    ChatManager chatManager;
    CurrentUser currentUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Map<String, String> tokenParams = new TreeMap<>();

        ChatkitTokenProvider tokenProvider = new ChatkitTokenProvider(
                "https://us1.pusherplatform.io/services/chatkit_token_provider/v1/c090a50e-3e0e-4d05-96b0-a967ee4717ad/token?instance_id=v1:us1:c090a50e-3e0e-4d05-96b0-a967ee4717ad",
                "zan",
                tokenParams,
                new OkHttpClient()
        );

        chatManager = new ChatManager(
                INSTANCE_ID,
                getApplicationContext(),
                tokenProvider,
                null,
                LogLevel.VERBOSE
        );


        chatManager.connect(
                new UserSubscriptionListeners(
                        new CurrentUserListener() {
                            @Override
                            public void onCurrentUser(@NonNull CurrentUser user) {
                                Log.d(TAG, "onCurrentUser");
                                currentUser = user;

                                joinRoom();



                            }
                        },
                        new ErrorListener() {
                            @Override
                            public void onError(Error error) {
                                Log.d(TAG, "onError");
                            }
                        },
                        new RemovedFromRoomListener() {
                            @Override
                            public void removedFromRoom(Room room) {
                                Log.d(TAG, "removed from room");
                            }
                        }
                )
        );
    }

    void joinRoom(){
        int numberOfRooms = currentUser.rooms().size();
        if (numberOfRooms > 0){
            Log.d(TAG, "Rooms:");
            Iterator<Room> roomIterator = currentUser.rooms().iterator();
            while(roomIterator.hasNext()){
                Room room = roomIterator.next();
                Log.d(TAG, "Room: " + room);
            }
        }
        else {
            Log.d(TAG, "No Rooms! Will create one now");

            currentUser.createRoom(
                    "le-room",
                    new RoomListener() {
                        @Override
                        public void onRoom(Room room) {
                            Log.d(TAG, "Room created " + room);
                        }
                    },
                    new ErrorListener() {
                        @Override
                        public void onError(Error error) {
                            Log.d(TAG, "Failed creating room "+ error);
                        }
                    }

            );

        }
    }
}
