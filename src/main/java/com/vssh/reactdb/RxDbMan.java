package com.vssh.reactdb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.CallSuper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.vssh.dbmanplus.DbManPlus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * Created by varun on 31.08.15.
 *
 * RxJava extension for DbManPlus. Extend this instead of DbManPlus to use query observables.
 */
public abstract class RxDbMan extends DbManPlus {

    private static List<PublishSubject<String>> mSubjectList = new ArrayList<>();

    /**
     * Instantiate a new DB Helper.
     * <br> SQLiteOpenHelpers are statically cached so they (and their internally cached SQLiteDatabases) will be reused for concurrency
     *
     * @param context Any {@link Context} belonging to your package.
     * @param name    The database name. This may be anything you like. Adding a file extension is not required and any file extension you would like to use is fine.
     * @param version the database version.
     */
    public RxDbMan(Context context, String name, int version) {
        super(context, name, version);
    }

    @Override
    public abstract void onCreate(SQLiteDatabase db);

    @Override
    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    /**
     * Register a query with the Database Manager, will run a query on the database every time the
     * underlying database changes
     * @param sql Raw SQL String
     * @param selectionArgs
     * @param queryTables List of tables of interest
     * @param debounceTime Minimum time between updates
     * @return Observable, subscribe to it to receive updated queries
     */
    @CheckResult
    public Observable<Cursor> registerRawQuery(final String sql, final String[] selectionArgs, @NonNull final List<String> queryTables, final int debounceTime) {
        final RxDbMan this_ = this;
        final PublishSubject<String> subject = PublishSubject.create();
        mSubjectList.add(subject);

        return subject
                .startWith("*")
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String s) {
                        return queryTables.contains(s) || s.equals("*");
                    }
                })
                .debounce(debounceTime, TimeUnit.MILLISECONDS)
                .map(new Func1<String, Cursor>() {
                    @Override
                    public Cursor call(String s) {
                        return this_.rawQuery(sql, selectionArgs);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        if (!subject.hasObservers()) {
                            RxDbMan.mSubjectList.remove(subject);
                        }
                    }
                });
    }

    /**
     * Register a query with the Database Manager, will run a query on the database every time the
     * underlying database changes
     * @param tableNames Table names to run the query on
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param groupBy
     * @param having
     * @param sortOrder
     * @param limit Maximum number of rows
     * @param queryTables List of tables of interest (Can be null if tableNames is a single table or
     *                    multiple tables CSV)
     * @param debounceTime Minimum time between updates
     * @return Observable, subscribe to it to receive updated queries
     */
    @CheckResult
    public Observable<Cursor> registerQuery(final String tableNames, final String[] projection, final String selection, final String[] selectionArgs, final String groupBy,
                                            final String having, final String sortOrder, final String limit, @Nullable final List<String> queryTables, final int debounceTime) {
        final RxDbMan this_ = this;
        final List<String> qTables;
        if(queryTables == null) {
            qTables = Arrays.asList(tableNames.split("\\s*,\\s*"));
        }
        else {
            qTables = queryTables;
        }
        final PublishSubject<String> subject = PublishSubject.create();
        mSubjectList.add(subject);

        return subject
                .startWith("*")
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String s) {
                        //Log.d("observed table: ", s);
                        return qTables.contains(s) || s.equals("*");
                    }
                })
                .debounce(debounceTime, TimeUnit.MILLISECONDS)
                .map(new Func1<String, Cursor>() {
                    @Override
                    public Cursor call(String s) {
                        //Log.d("run query: ", tableNames);
                        return this_.query(tableNames, projection, selection, selectionArgs, groupBy, having, sortOrder, limit);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        if (!subject.hasObservers()) {
                            //Log.d("unsubscribe from query", tableNames);
                            RxDbMan.mSubjectList.remove(subject);
                        }
                    }
                });
    }

    private void triggerSubjects(final String tableName) {
        int count = 0;
        for(final PublishSubject<String> sub : mSubjectList) {
            count++;
            //Log.d("triggering on table: ", tableName + count);
            Observable
                    .create(new Observable.OnSubscribe<String>() {
                        @Override
                        public void call(Subscriber<? super String> subscriber) {
                            try {
                                if (!subscriber.isUnsubscribed()) {
                                    subscriber.onNext(tableName);
                                }
                            } catch (Exception e) {
                                subscriber.onError(e);
                            }
                        }
                    })
                    .subscribeOn(Schedulers.immediate())
                    .observeOn(Schedulers.immediate())
                    .subscribe(sub);
        }
    }

    /**
     * Insert a row into the database
     * @param tableName
     * @param initialValues
     * @param conflictAlgorithm
     * @return row ID if successful, else -1
     */
    public long insertWithOnConflict(String tableName, ContentValues initialValues, int conflictAlgorithm) {
        long rowId = super.insertWithOnConflict(tableName, initialValues, conflictAlgorithm);
        triggerSubjects(tableName);
        return rowId;
    }

    /**
     * Insert multiple rows at once as a single transaction
     * @param tableName
     * @param values
     * @return number of rows inserted
     */
    public int bulkInsert(String tableName, ContentValues[] values) {
        int numInserted = super.bulkInsert(tableName, values);
        triggerSubjects(tableName);
        return numInserted;
    }

    /**
     * Delete from database
     * @param tableName
     * @param selection
     * @param selectionArgs
     * @return number of deleted rows
     */
    public int delete(String tableName, String selection, String[] selectionArgs) {
        int count = super.delete(tableName, selection, selectionArgs);
        triggerSubjects(tableName);
        return count;
    }

    /**
     * Update rows in the database
     * @param tableName
     * @param values
     * @param selection
     * @param selectionArgs
     * @param conflictAlgorithm
     * @return number of rows updated
     */
    @CallSuper
    public int updateWithOnConflict(String tableName, ContentValues values, String selection, String[] selectionArgs, int conflictAlgorithm) {
        int count = super.updateWithOnConflict(tableName, values, selection, selectionArgs, conflictAlgorithm);
        triggerSubjects(tableName);
        return count;
    }
}
