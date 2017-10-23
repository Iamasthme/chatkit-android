package com.pusher.chatkit

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.pusher.platform.Instance
import com.pusher.platform.logger.AndroidLogger
import com.pusher.platform.logger.LogLevel
import com.pusher.platform.tokenProvider.TokenProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class ChatManager(
        instanceId: String,
        context: Context,
        val tokenProvider: TokenProvider? = null,
        val tokenParams: ChatkitTokenParams? = null,
        logLevel: LogLevel = LogLevel.DEBUG
){

    companion object {
        val GSON = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
    }

    class Builder {

        private var instanceId: String? = null
        private var context: Context? = null
        private var tokenProvider: TokenProvider? = null
        private var tokenParams: ChatkitTokenParams? = null
        private var logLevel = LogLevel.DEBUG

        fun instanceId(instanceId: String): Builder{
            this.instanceId = instanceId
            return this
        }

        fun context(context: Context): Builder{
            this.context = context
            return this
        }

        fun tokenProvider(tokenProvider:  TokenProvider): Builder{
            this.tokenProvider = tokenProvider
            return this
        }

        fun tokenParams(tokenParams: ChatkitTokenParams): Builder{
            this.tokenParams = tokenParams
            return this
        }

        fun logLevel(logLevel: LogLevel): Builder{
            this.logLevel = logLevel
            return this
        }

        fun build(): ChatManager {
            if(instanceId == null){
                throw Error("setInstanceId() not called")
            }
            if(context == null){
                throw Error("setContext() not called")
            }
            if(tokenProvider == null){
                throw Error("setTokenProvider() not called")
            }

            return ChatManager(instanceId!!, context!!, tokenProvider, tokenParams, logLevel)
        }
    }

    var currentUser: CurrentUser? = null
    val serviceName = "chatkit"
    val serviceVersion = "v1"
    val logger = AndroidLogger(logLevel)

    val instance = Instance(
            instanceId = instanceId,
            serviceName = serviceName,
            serviceVersion = serviceVersion,
            context = context,
            logger = logger
    )

    val userStore = GlobalUserStore(
            instance = instance,
            logger = logger,
            tokenProvider = tokenProvider,
            tokenParams = tokenParams
    )

    var userSubscription: UserSubscription? = null //Initialised when connect() is called.

    fun connect(
            listeners: UserSubscriptionListeners
    ){
        val mainThreadListeners = ThreadedUserSubscriptionListeners.from(
                listeners = listeners,
                thread = Handler(Looper.getMainLooper()))

        val path = "users"
        this.userSubscription = UserSubscription(
                instance = instance,
                path = path,
                userStore = userStore,
                tokenProvider = tokenProvider!!,
                tokenParams = null,
                logger = logger,
                listeners = mainThreadListeners
        )
    }
}

class UserSubscriptionListeners @JvmOverloads constructor(
        val currentUserListener: CurrentUserListener = CurrentUserListener{},
        val errorListener: ErrorListener = ErrorListener {},
        val removedFromRoomListener: RemovedFromRoomListener = RemovedFromRoomListener {}
)

class ThreadedUserSubscriptionListeners
private constructor(
        val currentUserListener: CurrentUserListener,
        val errorListener: ErrorListener,
        val removedFromRoomListener: RemovedFromRoomListener
)
{
    companion object {
        fun from(listeners: UserSubscriptionListeners, thread: Handler): ThreadedUserSubscriptionListeners{
            return ThreadedUserSubscriptionListeners(
                    currentUserListener = CurrentUserListener { user -> thread.post { listeners.currentUserListener.onCurrentUser(user) } },
                    errorListener = ErrorListener { error -> listeners.errorListener.onError(error) },
                    removedFromRoomListener = RemovedFromRoomListener { room -> listeners.removedFromRoomListener.removedFromRoom(room) }
            )
        }
    }
}

//
//enum class EventType(type: String){
//    INITIAL_STATE("initial_state"),
//    ADDED_TO_ROOM("added_to_room"),
//    REMOVED_FROM_ROOM("removed_from_room"),
//    NEW_MESSAGE("new_message"),
//    ROOM_UPDATED("room_updated"),
//    ROOM_DELETED("room_deleted"),
//    USER_JOINED("user_joined"),
//    USER_LEFT("user_left")
//}

data class InitialState(
        val rooms: List<Room>, //TODO: might need to use a different subsctructure for this
        val currentUser: User
)

data class AddedToRoom(
        val room: Room
)

data class RemovedFromRoomPayload(
        val roomId: Int
)

data class Message(
        val id: Int,
        val userId: String,
        val roomId: Int,
        val text: String,
        val createdAt: String,
        val updatedAt: String,

        var user: User?,
        var room: Room?
)

data class RoomUpdated(
        val room: Room
)

data class RoomDeleted(
        val roomId: Int
)

data class UserJoined(
        val roomId: Int,
        val userId: String
)

data class UserLeft(
        val roomId: Int,
        val userId: String
)


data class ChatEvent(
        val eventName: String,
        val userId: String? = null,
        val timestamp: String,
        val data: JsonElement)

class User(
        val id: String,
        val createdAt: String,
        var updatedAt: String,

        var name: String?,
        var avatarURL: String?,
        var customData: CustomData?
) {
    fun updateWithPropertiesOfUser(user: User) {
        updatedAt = user.updatedAt
        name = user.name
        avatarURL = user.avatarURL
        customData = user.customData
    }
}


class RoomStore(val instance: Instance, val rooms: ConcurrentMap<Int, Room>) {

    fun setOfRooms(): Set<Room>  = rooms.values.toSet()

    fun addOrMerge(room: Room) {
        if (rooms[room.id] != null){
            rooms[room.id]!!.updateWithPropertiesOfRoom(room)
        }
        else{
            rooms.put(room.id, room)
        }
    }
}

class UserStore {
    private val members: ConcurrentMap<String, User>
    init {
        members = ConcurrentHashMap()
    }

    fun addOrMerge(user: User){
        if(members[user.id] != null){
            members[user.id]!!.updateWithPropertiesOfUser(user)
        }
        else{
            members.put(user.id, user)
        }
    }
}

typealias CustomData = MutableMap<String, String>

data class Room(
        val id: Int,
        val createdById: String,
        var name: String,
        var isPrivate: Boolean,
        val createdAt: String,
        var updatedAt: String,
        var deletedAt: String,
        var memberUserIds: List<String>,
        private var userStore: UserStore?
){

    fun userStore(): UserStore {
        if(userStore == null) userStore = UserStore()
        return userStore!!
    }


    fun updateWithPropertiesOfRoom(updatedRoom: Room){
        name = updatedRoom.name
        isPrivate = updatedRoom.isPrivate
        updatedAt = updatedRoom.updatedAt
        deletedAt = updatedRoom.deletedAt
        memberUserIds = updatedRoom.memberUserIds
    }
}
