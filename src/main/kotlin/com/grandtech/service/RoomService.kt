package com.grandtech.service

import com.grandtech.model.Room
import com.grandtech.repository.RoomRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/** Delegates all [Room] persistence operations to [RoomRepository]. */
@ApplicationScoped
class RoomService {

    /** Repository that executes all Cypher queries for [Room] nodes. */
    @Inject
    lateinit var roomRepository: RoomRepository

    /** Creates a new [Room] linked to [schoolFedUid]; returns null if the school does not exist. */
    fun createRoom(schoolFedUid: String, room: Room): Room? =
        roomRepository.createRoom(schoolFedUid, room)

    /** Returns all rooms belonging to [schoolFedUid], ordered by name. */
    fun listRooms(schoolFedUid: String): List<Room> =
        roomRepository.listRooms(schoolFedUid)

    /** Updates mutable fields of [roomId] under [schoolFedUid]; returns null if not found. */
    fun updateRoom(schoolFedUid: String, roomId: String, room: Room): Room? =
        roomRepository.updateRoom(schoolFedUid, roomId, room)

    /** Deletes [roomId] under [schoolFedUid]; returns false if the room was not found. */
    fun deleteRoom(schoolFedUid: String, roomId: String): Boolean =
        roomRepository.deleteRoom(schoolFedUid, roomId)
}