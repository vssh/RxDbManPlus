# reactdb
[RxJava](https://github.com/ReactiveX/RxJava/wiki) extension for [dbmanplus](https://github.com/vssh/dbmanplus).

It provides the option of registering queries with the database manager. Registered queries receive updates whenever relevant changes are made to the database.

## Usage
`reactdb` is an extension of `dbmanplus`. All the functionality of `dbmanplus` is also available while using `reactdb`.
In addition `reactdb` provides the possibility of registering your queries to receive updates as the database is updated. It uses the RxJava library to provide this functionality.
**NOTE: `Rxjava` is required to use `reactdb`**

### Extend RxDbMan class
Extend `RxDbMan` class instead of `DbManPlus`. The format is the same. Check `dbmanplus` documentation for usage.

### registerQuery
`registerQuery` takes the same parameters as `query` with a few additions. If `tableNames` is a single table or a comma separated string of table names, `queryTables` can be null. Otherwise, it should be a list of tables, anz changes to which will perform a new query. `debounceTime` is the minimum time between queries in milliseconds. It should be set to the maximum rate the observer can handle.
It returns an `Observable` that can be used as any `RxJava` `Observable`.
``` java
Observable<Cursor> observable =  registerQuery(tableNames, projection, selection, selectionArgs,
                                    groupBy, having, sortOrder, limit, queryTables, debounceTime);
```
**NOTE: The returned `Observable` contains a `Cursor`. This `Cursor` must be closed each time as it keeps an open connection to the database.
Closing the `Cursor` only one last time while destroying the context is NOT acceptable and will leak database connections.**

### registerRawQuery
`registerRawQuery` is similar to `rawQuery`, except that `queryTables` as a list of tables and `debounceTime` must be specified.
``` java
Observable<Cursor> observable =  registerRawQuery(sql, selectionArgs, queryTables, debounceTime);
```
**NOTE: The returned `Observable` contains a `Cursor`. This `Cursor` must be closed each time as it keeps an open connection to the database.
Closing the `Cursor` only once at the end while destroying the context is NOT acceptable and will leak database connections.**

### Working with returned `Observable`
``` java
Observable<Cursor> observable = dbManager.registerRawQuery(sql, selectionArgs, queryTables, 100);
mSubscription = observable
        .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())              //requires RxAndroid
        .subscribe(new Action1<Cursor>() {
             @Override
             public void call(Cursor cursor) {
                 //do something...
                 cursor.close();
             }
        }, new Action1<Throwable>() {
             @Override
             public void call(Throwable throwable) {
                 throwable.printStackTrace();
             }
        });
```
**NOTE: It is recommended to call `mSubscription.unsubscribe()` when updates are no longer needed or at the end of the lifecycle**