package com.google.ar.core.examples.java.common.helpers;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.SyncStateContract;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import static android.content.ContentValues.TAG;

public class DatabaseHelper extends SQLiteOpenHelper{

    public static final String CREATE_TREEDATABASE = "create table "+Constants.TREE_TABLE_NAME
            +"(id INTEGER PRIMARY KEY,"+
            "image varchar,"+
             "depth varchar, " +
            "rawdepth varchar, " +
            "confidence varchar," +
//            "height real," +
            "reldepth varchar," +
            "mask varchar," +
            "cloud varchar)";

    public static final String CREATE_HEIGHTDATABASE = "create table "+Constants.HEIGHT_TABLE_NAME
            +"(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "tree_id INTEGER," +
            "contour_id INTEGER,"+
            "height real," +
            "CONSTRAINT fk_treedata " +
            "FOREIGN KEY (tree_id) REFERENCES "+Constants.TREE_TABLE_NAME+"(id))";

    public static final String CREATE_INTRINSICSDATABASE = "create table "+Constants.INTRI_TABLE_NAME
            +"(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "fx real," +
            "fy real,"+
            "cx real," +
            "cy real)";

    private Context mContext;

    public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory , int version) {
        super(context, name, factory, version);
        mContext = context;
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        //首次创建时的回调
        Log.d(TAG,"创建数据库时回调...");

        db.execSQL(CREATE_TREEDATABASE);
        db.execSQL(CREATE_HEIGHTDATABASE);
        db.execSQL(CREATE_INTRINSICSDATABASE);

//        Toast.makeText(mContext, "数据库创建成功", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //当版本升级时回调
        Log.d(TAG,"升级数据库...");
    }
}
