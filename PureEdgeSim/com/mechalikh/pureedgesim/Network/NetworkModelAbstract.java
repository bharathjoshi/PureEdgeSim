package com.mechalikh.pureedgesim.Network;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSimEntity;
import org.cloudbus.cloudsim.core.events.SimEvent;
import com.mechalikh.pureedgesim.DataCentersManager.DataCenter;
import com.mechalikh.pureedgesim.ScenarioManager.SimulationParameters;
import com.mechalikh.pureedgesim.ScenarioManager.SimulationParameters.TYPES;
import com.mechalikh.pureedgesim.SimulationManager.SimulationManager;
import com.mechalikh.pureedgesim.TasksGenerator.Task;

public abstract class NetworkModelAbstract extends CloudSimEntity {
	public static final int base = 4000;
	public static final int SEND_REQUEST_FROM_ORCH_TO_DESTINATION = base + 1;
	protected static final int UPDATE_PROGRESS = base + 2;
	public static final int DOWNLOAD_CONTAINER = base + 3;
	public static final int SEND_REQUEST_FROM_DEVICE_TO_ORCH = base + 4;
	public static final int SEND_RESULT_FROM_ORCH_TO_DEV = base + 5;
	public static final int SEND_UPDATE_FROM_DEVICE_TO_ORCH = base + 6;
	public static final int SEND_RESULT_TO_ORCH = base + 7;
	// the list where the current (and the previous)
	// transferred file are stored
	protected List<FileTransferProgress> transferProgressList;
	protected SimulationManager simulationManager;
	protected double bwUsage = 0;

	public NetworkModelAbstract(SimulationManager simulationManager) {
		super(simulationManager.getSimulation());
		setSimulationManager(simulationManager);
		transferProgressList = new ArrayList<>();
	}

	private void setSimulationManager(SimulationManager simulationManager) {
		this.simulationManager = simulationManager;
	}

	public List<FileTransferProgress> getTransferProgressList() {
		return transferProgressList;
	}

	protected abstract void updateTasksProgress();

	protected abstract void updateTransfer(FileTransferProgress transfer);

	protected abstract void updateEnergyConsumption(FileTransferProgress transfer, String type);

	protected abstract void transferFinished(FileTransferProgress transfer);

	protected boolean sameLanIsUsed(Task task1, Task task2) {
		// The trasfers share same Lan of they have one device in common
		// Compare orchestrator
		return ((task1.getOrchestrator() == task2.getOrchestrator())
				|| (task1.getOrchestrator() == task2.getVm().getHost().getDatacenter())
				|| (task1.getOrchestrator() == task2.getEdgeDevice())
				|| (task1.getOrchestrator() == task2.getRegistry())

				// Compare origin device
				|| (task1.getEdgeDevice() == task2.getOrchestrator())
				|| (task1.getEdgeDevice() == task2.getVm().getHost().getDatacenter())
				|| (task1.getEdgeDevice() == task2.getEdgeDevice()) || (task1.getEdgeDevice() == task2.getRegistry())

				// Compare offloading destination
				|| (task1.getVm().getHost().getDatacenter() == task2.getOrchestrator())
				|| (task1.getVm().getHost().getDatacenter() == task2.getVm().getHost().getDatacenter())
				|| (task1.getVm().getHost().getDatacenter() == task2.getEdgeDevice())
				|| (task1.getVm().getHost().getDatacenter() == task2.getRegistry()));
	}

	protected boolean wanIsUsed(FileTransferProgress fileTransferProgress) {
		return ((fileTransferProgress.getTransferType() == FileTransferProgress.Type.TASK
				&& ((DataCenter) fileTransferProgress.getTask().getVm().getHost().getDatacenter()).getType().equals(TYPES.CLOUD))
				// If the offloading destination is the cloud

				|| (fileTransferProgress.getTransferType() == FileTransferProgress.Type.CONTAINER
						&& (fileTransferProgress.getTask().getRegistry()== null || fileTransferProgress.getTask().getRegistry().getType() == TYPES.CLOUD))
				// Or if containers will be downloaded from registry

				|| (fileTransferProgress.getTask().getOrchestrator().getType() == SimulationParameters.TYPES.CLOUD));
	         	// Or if the orchestrator is deployed in the cloud

	}

	protected void updateBandwidth(FileTransferProgress transfer) {
		double bandwidth;
		if (wanIsUsed(transfer)) {
			// The bandwidth will be limited by the minimum value
			// If the lan bandwidth is 1 mbps and the wan bandwidth is 4 mbps
			// It will be limited by the lan, so we will choose the minimum
			bandwidth = Math.min(transfer.getLanBandwidth(), transfer.getWanBandwidth());
		} else
			bandwidth = transfer.getLanBandwidth();
		transfer.setCurrentBandwidth(bandwidth);
	}

	protected double getLanBandwidth(double remainingTasksCount_Lan) {
		return (SimulationParameters.BANDWIDTH_WLAN / (remainingTasksCount_Lan > 0 ? remainingTasksCount_Lan : 1));
	}

	protected double getWanBandwidth(double remainingTasksCount_Wan) {
		return (SimulationParameters.WAN_BANDWIDTH / (remainingTasksCount_Wan > 0 ? remainingTasksCount_Wan : 1));
	}

	@Override
	protected void startEntity() {
		// do something or schedule events
	}

	@Override
	public void processEvent(SimEvent ev) {
		// process the scheduled events
	}

	public double getWanUtilization() {
		int wanTasks = 0;
		for (FileTransferProgress fileTransferProgress : transferProgressList) {
			if (fileTransferProgress.getRemainingFileSize() > 0 && wanIsUsed(fileTransferProgress)) {
				wanTasks++;
				bwUsage += fileTransferProgress.getRemainingFileSize();
			}
		}
		bwUsage = (wanTasks > 0 ? bwUsage / wanTasks : 0) / 1000;
		return Math.min(bwUsage, SimulationParameters.WAN_BANDWIDTH/1000);
	}

}
