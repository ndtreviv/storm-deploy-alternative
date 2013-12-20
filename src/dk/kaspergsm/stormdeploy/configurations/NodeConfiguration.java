package dk.kaspergsm.stormdeploy.configurations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jclouds.scriptbuilder.domain.Statement;
import dk.kaspergsm.stormdeploy.Tools;
import dk.kaspergsm.stormdeploy.configurations.SystemTools.PACKAGE_MANAGER;
import dk.kaspergsm.stormdeploy.userprovided.Configuration;
import dk.kaspergsm.stormdeploy.userprovided.Credential;

/**
 * @author Kasper Grud Skat Madsen
 */
public class NodeConfiguration {
	
	public static List<Statement> getCommands(Credential credentials, Configuration config, List<String> zookeeperHostnames, String nimbusHostname, String uiHostname) {
		List<Statement> commands = new ArrayList<Statement>();
		
		// Install system tools and update all packages to increase security
		commands.addAll(SystemTools.init(PACKAGE_MANAGER.APT));

		// Install and configure s3cmd (to allow communication with Amazon S3)
		commands.addAll(S3CMD.install(PACKAGE_MANAGER.APT));
		commands.addAll(S3CMD.configure(credentials.getIdentity(), credentials.getCredential()));
		
		// Conditional - Download and configure ZeroMQ (including jzmq binding)
		commands.addAll(ZeroMQ.download());
		commands.addAll(ZeroMQ.compile());
		commands.addAll(ZeroMQ.installJavaBinding());
		
		// Download and configure snorm-deploy-alternative (before anything with supervision is started)
		commands.addAll(StormDeployAlternative.download());
		commands.addAll(StormDeployAlternative.writeConfigurationFiles(Tools.getWorkDir() + "conf" + File.separator + "configuration.yaml", Tools.getWorkDir() + "conf" + File.separator + "credential.yaml"));
		commands.addAll(StormDeployAlternative.writeLocalSSHKeys());
		
		// Download Storm
		commands.addAll(Storm.download(config.getStormRemoteLocation()));
		
		// Download Zookeeper
		commands.addAll(Zookeeper.download(config.getZKLocation()));
		
		// Execute custom code, if user provided (pre config)
		if (config.getRemoteExecPreConfig().size() > 0)
			commands.addAll(Tools.runCustomCommands(config.getRemoteExecPreConfig()));
		
		// Configure Zookeeper (update configurationfiles)
		commands.addAll(Zookeeper.configure(zookeeperHostnames));
		
		// Configure Storm (update configurationfiles)
		commands.addAll(Storm.configure(nimbusHostname, zookeeperHostnames));
		
		// Execute custom code, if user provided (post config)
		if (config.getRemoteExecPostConfig().size() > 0)
			commands.addAll(Tools.runCustomCommands(config.getRemoteExecPostConfig()));
		
		
		/**
		 * Start daemons (only on correct nodes, and under supervision)
		 */
		commands.addAll(Zookeeper.startDaemonSupervision());
		commands.addAll(Storm.startNimbusDaemonSupervision());
		commands.addAll(Storm.startSupervisorDaemonSupervision());
		commands.addAll(Storm.startUIDaemonSupervision());
		
		/**
		 * Start memory manager (to sharing of resources among Java processes)
		 * 	requires StormDeployAlternative is installed remotely
		 */
		commands.addAll(StormDeployAlternative.runMemoryMonitor());
		
		// Return commands
		return commands;
	}
}