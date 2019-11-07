package com.vssh.rxdbmanplus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.CallSuper;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
public abstract class RxDbManPlus extends DbManPlus {

    private static List<PublishSubject<String>> mSubjectList = new ArrayList<>();

    /**
     * Instantiate a new DB Helper.
     * <br> SQLiteOpenHelpers are statically cached so they (and their internally cached SQLiteDatabases) will be reused for concurrency
     *
     * @param context Any {@link Context} belonging to your package.
     * @param name    The database name. This may be anything you like. Adding a file extension is not required and any file extension you would like to use is fine.
     * @param version the database version.
     */
    public RxDbManPlus(Context context, String name, int version) {
        super(context, name, version);
    }

    @Override
    public abstract void onCreate(SQLiteDatabase db);

    @Override
    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    /**
     * Register a query with the Database Manager, will run a query on the database every time the
     * underlying database changes
     * @param sql the SQL query. The SQL string must not be ; terminated
     * @param selectionArgs You may include ?s in where clause in the query,
     *     which will be replaced by the values from selectionArgs. The
     *     values will be bound as Strings.
     * @param queryTables List of tables of interest
     * @param debounceTime Minimum time between updates in milliseconds
     * @return Observable, subscribe to it to receive updated queries
     */
    @CheckResult
    public Observable<Cursor> registerRawQuery(final String sql, final String[] selectionArgs, @NonNull final List<String> queryTables, final int debounceTime) {
        final RxDbManPlus this_ = this;
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
                            RxDbManPlus.mSubjectList.remove(subject);
                        }
                    }
                });
    }

    /**
     * Register a query with the Database Manager, will run a query on the database every time the
     * underlying database changes
     * @param tableNames Table names to run the query on
     * @param projection A list of which columns to return. Passing
     *   null will return all columns, which is discouraged to prevent
     *   reading data from storage that isn't going to be used.
     * @param selection A filter declaring which rows to return,
     *   formatted as an SQL WHERE clause (excluding the WHERE
     *   itself). Passing null will return all rows for the given URL.
     * @param selectionArgs You may include ?s in selection, which
     *   will be replaced by the values from selectionArgs, in order
     *   that they appear in the selection. The values will be bound
     *   as Strings.
     * @param groupBy A filter declaring how to group rows, formatted
     *   as an SQL GROUP BY clause (excluding the GROUP BY
     *   itself). Passing null will cause the rows to not be grouped.
     * @param having A filter declare which row groups to include in
     *   the cursor, if row grouping is being used, formatted as an
     *   SQL HAVING clause (excluding the HAVING itself).  Passing
     *   null will cause all row groups to be included, and is
     *   required when row grouping is not being used.
     * @param sortOrder How to order the rows, formatted as an SQL
     *   ORDER BY clause (excluding the ORDER BY itself). Passing null
     *   will use the default sort order, which may be unordered.
     * @param limit Maximum number of rows
     * @param queryTables List of tables of interest (Can be null if tableNames is a single table or
     *                    multiple tables CSV)
     * @param debounceTime Minimum time between updates in milliseconds
     * @return Observable, subscribe to it to receive updated queries
     */
    @CheckResult
    public Observable<Cursor> registerQuery(final String tableNames, final String[] projection, final String selection, final String[] selectionArgs, final String groupBy,
                                            final String having, final String sortOrder, final String limit, @Nullable final List<String> queryTables, final int debounceTime) {
        final RxDbManPlus this_ = this;
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
                            RxDbManPlus.mSubjectList.remove(subject);
                        }
                    }
                });
    }

    private void triggerSubjects(final String tableName) {
        for(final PublishSubject<String> sub : mSubjectList) {
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
     * @param tableName the table to insert the row into
     * @param initialValues this map contains the initial column values for the
     *            row. The keys should be the column names and the values the
     *            column values
     * @param conflictAlgorithm for insert conflict resolver
     * @return row ID if successful, else -1
     */
    public long insertWithOnConflict(String tableName, ContentValues initialValues, int conflictAlgorithm) {
        long rowId = super.insertWithOnConflict(tableName, initialValues, conflictAlgorithm);
        triggerSubjects(tableName);
        return rowId;
    }

    /**
     * Insert multiple rows at once as a single transaction
     * @param tableName the table to update in
     * @param values a map from column names to new column values. null is a
     *            valid value that will be translated to NULL.
     * @return number of rows inserted
     */
    public int bulkInsert(String tableName, ContentValues[] values) {
        int numInserted = super.bulkInsert(tableName, values);
        triggerSubjects(tableName);
        return numInserted;
    }

    /**
     * Delete from database
     * @param tableName the table to update in
     * @param selection the optional WHERE clause to apply when deleting.
     *            Passing null will update all rows.
     * @param selectionArgs You may include ?s in the where clause, which
     *            will be replaced by the values from whereArgs. The values
     *            will be bound as Strings.
     * @return number of deleted rows
     */
    public int delete(String tableName, String selection, String[] selectionArgs) {
        int count = super.delete(tableName, selection, selectionArgs);
        triggerSubjects(tableName);
        return count;
    }

    /**
     * Update rows in the database
     * @param tableName the table to update in
     * @param values a map from column names to new column values. null is a
     *            valid value that will be translated to NULL.
     * @param selection the optional WHERE clause to apply when updating.
     *            Passing null will update all rows.
     * @param selectionArgs You may include ?s in the where clause, which
     *            will be replaced by the values from whereArgs. The values
     *            will be bound as Strings.
     * @return number of rows updated
     */
    @CallSuper
    public int updateWithOnConflict(String tableName, ContentValues values, String selection, String[] selectionArgs, int conflictAlgorithm) {
        int count = super.updateWithOnConflict(tableName, values, selection, selectionArgs, conflictAlgorithm);
        triggerSubjects(tableName);
        return count;
    }
}
