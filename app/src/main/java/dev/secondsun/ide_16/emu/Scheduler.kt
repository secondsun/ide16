package dev.secondsun.ide_16.emu

import kotlinx.coroutines.Job

class Scheduler {
    enum class Mode  { Run, Synchronize }
    var mode :Mode = Mode.Run;
    enum class Event  { Frame, Synchronized, Desynchronized } ;
    var event = Event.Frame

    var host :Job? = null;
    var active :Job? = null;
    var desynchronized: Boolean = false;

    fun enter()  {
        host = co_active();
        co_switch(active);
    }

    private fun co_switch(active: Job?) {
        TODO("Not yet implemented")
    }

    private fun co_active(): Job? {
        TODO("Not yet implemented")
    }

    fun leave( event_ :Event) {
        event = event_;
        active = co_active();
        co_switch(host);
    }

    auto resume(cothread_t thread) -> void {
        if(mode == Mode::Synchronize) desynchronized = true;
        co_switch(thread);
    }

    inline auto synchronizing() const -> bool {
        return mode == Mode::Synchronize;
    }

    inline auto synchronize() -> void {
        if(mode == Mode::Synchronize) {
            if(desynchronized) {
                desynchronized = false;
                leave(Event::Desynchronized);
            } else {
                leave(Event::Synchronized);
            }
        }
    }

    inline auto desynchronize() -> void {
        desynchronized = true;
    }

}