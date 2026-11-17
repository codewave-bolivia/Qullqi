package com.codewave.qullqi.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.codewave.qullqi.data.db.DatabaseFactory
import com.codewave.qullqi.db.QullqiDatabase

object AndroidDatabaseFactory {
    fun create(context: Context, name: String = "qullqi.db"): QullqiDatabase {
        val callback = object : AndroidSqliteDriver.Callback(QullqiDatabase.Schema) {
            // Android may open several connections; enable foreign keys on each of them.
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        }
        return DatabaseFactory.create(AndroidSqliteDriver(QullqiDatabase.Schema, context, name, callback = callback))
    }
}
