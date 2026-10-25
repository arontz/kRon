//! CPU, memory and network sampling, once a second, only while something shows it.

use std::sync::atomic::Ordering;
use std::time::{Duration, Instant};

use serde::Serialize;
use sysinfo::{Networks, System};
use tauri::{AppHandle, Emitter, Manager};

use crate::Shared;

#[derive(Clone, Debug, Default, Serialize)]
pub struct Stats {
    pub cpu: f32,
    pub cores: usize,
    /// Last 30 CPU samples, oldest first.
    pub cpu_history: Vec<f32>,
    pub mem_used: u64,
    pub mem_total: u64,
    /// Bytes per second.
    pub down: u64,
    pub up: u64,
}

pub fn run(app: AppHandle) {
    let shared = app.state::<Shared>().inner().clone();
    let mut sys = System::new();
    let mut nets = Networks::new_with_refreshed_list();
    let mut history: Vec<f32> = Vec::new();
    let mut last = Instant::now();
    sys.refresh_cpu_usage();

    loop {
        std::thread::sleep(Duration::from_millis(1000));
        let wanted = shared.flyout_open.load(Ordering::Relaxed) || shared.settings.lock().unwrap().page == "system";
        if !wanted {
            continue;
        }

        sys.refresh_cpu_usage();
        sys.refresh_memory();
        nets.refresh(true);
        let secs = last.elapsed().as_secs_f64().max(0.2);
        last = Instant::now();

        let (mut down, mut up) = (0u64, 0u64);
        for (_, data) in &nets {
            down += data.received();
            up += data.transmitted();
        }

        let cpu = sys.global_cpu_usage();
        history.push(cpu);
        if history.len() > 30 {
            history.remove(0);
        }

        let stats = Stats {
            cpu,
            cores: sys.cpus().len(),
            cpu_history: history.clone(),
            mem_used: sys.used_memory(),
            mem_total: sys.total_memory(),
            down: (down as f64 / secs) as u64,
            up: (up as f64 / secs) as u64,
        };
        *shared.stats.lock().unwrap() = stats.clone();
        let _ = app.emit("stats", stats);
    }
}
