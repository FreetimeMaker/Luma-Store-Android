package com.freetime.lumastore.notifications

import android.app.job.JobParameters
import android.app.job.JobService
import com.freetime.lumastore.data.DeveloperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        runningJob = scope.launch {
            try {
                val repository = DeveloperRepository()
                val session = repository.savedSession()
                if (session != null) {
                    val dashboard = repository.loadDashboard(session)
                    SystemNotificationManager(applicationContext)
                        .showNewNotifications(dashboard.notifications)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
