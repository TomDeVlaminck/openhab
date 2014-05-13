package be.devlaminck.openwebnet;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.myhome.fcrisciani.connector.MyHomeJavaConnector;
import com.myhome.fcrisciani.exception.MalformedCommandOPEN;

/**
 * OpenWebNet - OpenHab device communicator Based on code from Mauro Cicolella
 * (as part of the FREEDOMOTIC connector)
 * (https://code.google.com/p/freedomotic/
 * source/browse/plugins/devices/openwebnet) and on code of Flavio Fcrisciani
 * (https://github.com/fcrisciani/java-myhome-library)
 * 
 * @author Tom De Vlaminck
 * @serial 1.0
 * @since 1.5.0
 * 
 */
public class OpenWebNet extends Thread
{
	private static final Logger logger = LoggerFactory
			.getLogger(OpenWebNet.class);

	/*
	 * Initializations
	 */
	private String host = "10.0.6.12";
	private Integer port = 20000;
	private Date m_last_bus_scan = new Date(0);
	private Integer m_bus_scan_interval_secs = 120;
	public MyHomeJavaConnector myPlant = null;
	private MonitorSessionThread monitorSessionThread = null;

	/*
	 * OWN Diagnostic Frames
	 */
	private final static String LIGHTNING_DIAGNOSTIC_FRAME = "*#1*0##";
	private final static String AUTOMATIONS_DIAGNOSTIC_FRAME = "*#2*0##";
	private final static String ALARM_DIAGNOSTIC_FRAME = "*#5##";
	private final static String POWER_MANAGEMENT_DIAGNOSTIC_FRAME = "*#3##";

	/*
	 * Event listeners = they receive an object when something happens on the
	 * bus
	 */
	private List<IBticinoEventListener> m_event_listener_list = new LinkedList<IBticinoEventListener>();

	public OpenWebNet(String p_host, int p_port, int p_rescan_interval_secs)
	{
		host = p_host;
		port = p_port;
		m_bus_scan_interval_secs = p_rescan_interval_secs;
	}

	/*
	 * Sensor side
	 */
	public void onStart()
	{
		// create thread
		monitorSessionThread = new MonitorSessionThread(this, host, port);
		// start thread
		monitorSessionThread.start();
		// synchronizes the software with the system status
		initSystem();

		logger.info("Connected to [" + host + ":" + port + "], Rescan bus every [" + m_bus_scan_interval_secs + "] seconds");
		// reset the last bus scan date
		m_last_bus_scan = new Date();

		// start the processing thread
		start();
	}

	@SuppressWarnings("deprecation")
	public void onStop()
	{
		// TODO : add stop method on monitor thread that sets flag + does
		// interrupt
		// interrupt handler will then check flag and stop thread
		monitorSessionThread.stop();
		logger.info("Stopped OpenWebNet thread");
	}

	/*
	 * Actuator side
	 */
	public void onCommand(ProtocolRead c) throws IOException, Exception
	{
		try
		{
			myPlant.sendCommandAsync(OWNUtilities.createFrame(c), 1);
		} catch (MalformedCommandOPEN ex)
		{
			logger.error("onCommand error : " + ex.getMessage());
		}
	}

	@Override
	public void run()
	{
		try
		{
			while (true)
			{
				// Every x seconds do a full bus scan
				checkForBusScan();
				Thread.sleep(1000);
			}
		} catch (InterruptedException p_i_ex)
		{
			logger.error("Openwebnet.run, InterruptedException : "
					+ p_i_ex.getMessage());
		} catch (Exception p_i_ex)
		{
			logger.error("Openwebnet.run, Exception : " + p_i_ex.getMessage());
		}
	}

	private void checkForBusScan()
	{
		Date l_now = new Date();
		if (((l_now.getTime() - m_last_bus_scan.getTime()) / 1000) > m_bus_scan_interval_secs)
		{
			m_last_bus_scan = l_now;
			initSystem();
		}
	}

	// sends diagnostic frames to initialize the system
	public void initSystem()
	{
		try
		{
			logger.info("Sending " + LIGHTNING_DIAGNOSTIC_FRAME
					+ " frame to (re)initialize LIGHTNING");
			myPlant.sendCommandSync(LIGHTNING_DIAGNOSTIC_FRAME);
			logger.info("Sending " + AUTOMATIONS_DIAGNOSTIC_FRAME
					+ " frame to (re)initialize AUTOMATIONS");
			myPlant.sendCommandSync(AUTOMATIONS_DIAGNOSTIC_FRAME);
			logger.info("Sending " + ALARM_DIAGNOSTIC_FRAME
					+ " frame to (re)initialize ALARM");
			myPlant.sendCommandSync(ALARM_DIAGNOSTIC_FRAME);
			logger.info("Sending " + POWER_MANAGEMENT_DIAGNOSTIC_FRAME
					+ " frame to (re)initialize POWER MANAGEMENT");
			myPlant.sendCommandSync(POWER_MANAGEMENT_DIAGNOSTIC_FRAME);
		} catch (Exception e)
		{
			logger.error("initSystem failed : " + e.getMessage());
		}
	}

	public void notifyEvent(ProtocolRead p_i_event)
	{
		for (IBticinoEventListener l_event_listener : m_event_listener_list)
		{
			try
			{
				l_event_listener.handleEvent(p_i_event);
			} catch (Exception p_ex)
			{
				logger.error("notifyEvent, Exception : " + p_ex.getMessage());
			}
		}
	}

	public void addEventListener(IBticinoEventListener p_i_event_listener)
	{
		m_event_listener_list.add(p_i_event_listener);
	}

	public void removeEventListener(IBticinoEventListener p_i_event_listener)
	{
		m_event_listener_list.remove(p_i_event_listener);
	}

}