package ru.astar.tcprelay

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logs

    fun addLog(message: String) {
        _logs.value += message
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}