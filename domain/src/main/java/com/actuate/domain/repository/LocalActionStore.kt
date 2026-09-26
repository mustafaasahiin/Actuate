package com.actuate.domain.repository

import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerList
import com.actuate.domain.model.ServerListItem

interface LocalActionStore {
    suspend fun getActions(): List<ServerActionItem>
    suspend fun saveActions(actions: List<ServerActionItem>)
    suspend fun addAction(action: ServerActionItem)
    suspend fun getListItems(): List<ServerListItem>
    suspend fun saveListItems(items: List<ServerListItem>)
    suspend fun addListItem(item: ServerListItem)
    suspend fun updateListItemDone(id: String, done: Boolean)
    suspend fun clear() {}
    suspend fun getLists(): List<ServerList> =
        getListItems().groupBy { it.list }.map { (name, items) -> ServerList(name, items) }

    suspend fun getPendingSyncActions(): List<ServerActionItem>
    suspend fun markActionSynced(id: String)
    suspend fun getListNames(): Set<String> = emptySet()
    suspend fun saveListNames(names: Set<String>) {}
    suspend fun getDeletedListItemIds(): Set<String> = emptySet()
    suspend fun saveDeletedListItemIds(ids: Set<String>) {}
    suspend fun deleteAction(id: String) {}
}
