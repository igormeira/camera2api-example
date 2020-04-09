package com.igormeira.camera2api.async

import android.os.AsyncTask

class BaseAsyncTask<T>(
    private val executeListener: ExecuteListener<T>,
    private val finishedListener: FinishedListener<T>
) :
    AsyncTask<Void?, Void?, T>() {
    override fun doInBackground(vararg p0: Void?): T {
        return executeListener.onExecute()
    }

    override fun onPostExecute(resultado: T) {
        super.onPostExecute(resultado)
        finishedListener.onFinished(resultado)
    }

    interface ExecuteListener<T> {
        fun onExecute(): T
    }

    interface FinishedListener<T> {
        fun onFinished(result: T)
    }

}