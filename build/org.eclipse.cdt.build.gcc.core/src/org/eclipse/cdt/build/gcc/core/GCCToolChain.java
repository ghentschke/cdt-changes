/*******************************************************************************
 * Copyright (c) 2015, 2017 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.build.gcc.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.cdt.build.gcc.core.internal.Activator;
import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.build.IToolChain;
import org.eclipse.cdt.core.build.IToolChainProvider;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.dom.ast.gnu.cpp.GPPLanguage;
import org.eclipse.cdt.core.envvar.EnvironmentVariable;
import org.eclipse.cdt.core.envvar.IEnvironmentVariable;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.ExtendedScannerInfo;
import org.eclipse.cdt.core.parser.IExtendedScannerInfo;
import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.PlatformObject;

/**
 * The GCC toolchain. This is the base class for all GCC toolchains. It
 * represents GCC as found on the user's PATH. It can be overridden to change
 * environment variable settings.
 */
public class GCCToolChain extends PlatformObject implements IToolChain {

	private final IToolChainProvider provider;
	private final String id;
	private final Path path;
	private final IEnvironmentVariable pathVar;
	private final IEnvironmentVariable[] envVars;
	private final Map<String, String> properties = new HashMap<>();

	private String cCommand;
	private String cppCommand;
	private String[] commands;

	public GCCToolChain(IToolChainProvider provider, String id, String version) {
		this(provider, id, version, null, null);
	}

	public GCCToolChain(IToolChainProvider provider, String id, String version, Path[] path) {
		this(provider, id, version, path, null);
	}

	public GCCToolChain(IToolChainProvider provider, String id, String version, Path[] path, String prefix) {
		this.provider = provider;
		this.id = id;

		if (path != null && path.length > 0) {
			StringBuilder pathString = new StringBuilder();
			for (int i = 0; i < path.length; ++i) {
				pathString.append(path[i].toString());
				if (i < path.length - 1) {
					pathString.append(File.pathSeparator);
				}
			}
			this.path = path[0];
			this.pathVar = new EnvironmentVariable("PATH", pathString.toString(), IEnvironmentVariable.ENVVAR_PREPEND, //$NON-NLS-1$
					File.pathSeparator);
			this.envVars = new IEnvironmentVariable[] { pathVar };
		} else {
			this.path = null;
			this.pathVar = null;
			this.envVars = new IEnvironmentVariable[0];
		}
	}

	public GCCToolChain(IToolChainProvider provider, Path pathToToolChain, String arch,
			IEnvironmentVariable[] envVars) {
		this.provider = provider;
		this.path = pathToToolChain;

		// We include arch in the id since a compiler can support multiple arches.
		StringBuilder idBuilder = new StringBuilder("gcc-"); //$NON-NLS-1$
		if (arch != null) {
			idBuilder.append(arch);
		}
		idBuilder.append('-');
		idBuilder.append(pathToToolChain.toString());
		this.id = idBuilder.toString();

		properties.put(ATTR_ARCH, arch);

		IEnvironmentVariable pathVar = null;
		if (envVars != null) {
			for (IEnvironmentVariable envVar : envVars) {
				if (envVar.getName().equalsIgnoreCase("PATH")) { //$NON-NLS-1$
					pathVar = envVar;
				}
			}
		}
		
		if (pathVar == null) {
			// Make one with the directory containing out tool
			String name;
			// if (System.getenv("Path") != null) { //$NON-NLS-1$
			// name = "Path"; //$NON-NLS-1$
			// } else {
				name = "PATH"; //$NON-NLS-1$
			// }
			pathVar = new EnvironmentVariable(name, this.path.getParent().toString(),
					IEnvironmentVariable.ENVVAR_PREPEND, File.pathSeparator);
			if (envVars == null) {
				envVars = new IEnvironmentVariable[] { pathVar };
			} else {
				IEnvironmentVariable[] newVars = new IEnvironmentVariable[envVars.length + 1];
				System.arraycopy(envVars, 0, newVars, 0, envVars.length);
				newVars[envVars.length] = pathVar;
				envVars = newVars;
			}
		}
		this.pathVar = pathVar;
		this.envVars = envVars;
	}

	public Path getPath() {
		return path;
	}

	@Override
	public IToolChainProvider getProvider() {
		return provider;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getVersion() {
		return ""; //$NON-NLS-1$
	}

	@Override
	public String getName() {
		StringBuilder name = new StringBuilder("GCC"); //$NON-NLS-1$
		String os = getProperty(ATTR_OS);
		if (os != null) {
			name.append(' ');
			name.append(os);
		}

		String arch = getProperty(ATTR_ARCH);
		if (arch != null) {
			name.append(' ');
			name.append(arch);
		}

		if (path != null) {
			name.append(' ');
			name.append(path.toString());
		}

		return name.toString();
	}

	@Override
	public String getProperty(String key) {
		String value = properties.get(key);
		if (value != null) {
			return value;
		}

		// By default, we're a local GCC
		switch (key) {
		case ATTR_OS:
			return Platform.getOS();
		case ATTR_ARCH:
			if (Platform.getOS().equals(getProperty(ATTR_OS))) {
				return Platform.getOSArch();
			}
		}

		return null;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	@Override
	public void setProperty(String key, String value) {
		properties.put(key, value);
	}
	
	@Override
	public String getBinaryParserId() {
		// Assume local builds
		// TODO be smarter and use the id which should be the target
		switch (Platform.getOS()) {
		case Platform.OS_WIN32:
			return CCorePlugin.PLUGIN_ID + ".PE"; //$NON-NLS-1$
		case Platform.OS_MACOSX:
			return CCorePlugin.PLUGIN_ID + ".MachO64"; //$NON-NLS-1$
		default:
			return CCorePlugin.PLUGIN_ID + ".ELF"; //$NON-NLS-1$
		}
	}

	protected void addDiscoveryOptions(List<String> command) {
		command.add("-E"); //$NON-NLS-1$
		command.add("-P"); //$NON-NLS-1$
		command.add("-v"); //$NON-NLS-1$
		command.add("-dD"); //$NON-NLS-1$
	}

	@Override
	public IExtendedScannerInfo getScannerInfo(IBuildConfiguration buildConfig, List<String> commandStrings,
			IExtendedScannerInfo baseScannerInfo, IResource resource, URI buildDirectoryURI) {
		try {
			Path buildDirectory = Paths.get(buildDirectoryURI);

			Path command = Paths.get(commandStrings.get(0));
			List<String> commandLine = new ArrayList<>();
			if (command.isAbsolute()) {
				commandLine.add(command.toString());
			} else {
				commandLine.add(getCommandPath(command).toString());
			}

			if (baseScannerInfo != null && baseScannerInfo.getIncludePaths() != null) {
				for (String includePath : baseScannerInfo.getIncludePaths()) {
					commandLine.add("-I" + includePath); //$NON-NLS-1$
				}
			}

			addDiscoveryOptions(commandLine);
			commandLine.addAll(commandStrings.subList(1, commandStrings.size()));

			// Strip quotes from the args on Windows
			if (Platform.OS_WIN32.equals(Platform.getOS())) {
				for (int i = 0; i < commandLine.size(); i++) {
					String arg = commandLine.get(i);
					if (arg.contains("\"")) { //$NON-NLS-1$
						commandLine.set(i, arg.replaceAll("\"", "")); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}

			// Change output to stdout
			boolean haveOut = false;
			for (int i = 0; i < commandLine.size() - 1; ++i) {
				if (commandLine.get(i).equals("-o")) { //$NON-NLS-1$
					commandLine.set(i + 1, "-"); //$NON-NLS-1$
					haveOut = true;
					break;
				}
			}
			if (!haveOut) {
				commandLine.add("-o"); //$NON-NLS-1$
				commandLine.add("-"); //$NON-NLS-1$
			}

			// Change source file to a tmp file (needs to be empty)
			Path tmpFile = null;
			for (int i = 1; i < commandLine.size(); ++i) {
				if (!commandLine.get(i).startsWith("-")) { //$NON-NLS-1$
					// TODO optimize by dealing with multi arg options like -o
					Path filePath = buildDirectory.resolve(commandLine.get(i));
					IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(filePath.toUri());
					if (files.length > 0 && files[0].exists()) {
						// replace it with a temp file
						Path parentPath = filePath.getParent();
						String extension = files[0].getFileExtension();
						if (extension == null) {
							// Not sure if this is a reasonable choice when
							// there's
							// no extension
							extension = ".cpp"; //$NON-NLS-1$
						} else {
							extension = '.' + extension;
						}
						tmpFile = Files.createTempFile(parentPath, ".sc", extension); //$NON-NLS-1$
						commandLine.set(i, tmpFile.toString());
					}
				}
			}
			if (tmpFile == null) {
				// Have to assume there wasn't a source file. Add one in the
				// resource's container
				IPath parentPath = resource instanceof IFile ? resource.getParent().getLocation()
						: resource.getLocation();
				tmpFile = Files.createTempFile(parentPath.toFile().toPath(), ".sc", ".cpp"); //$NON-NLS-1$ //$NON-NLS-2$
				commandLine.add(tmpFile.toString());
			}

			return getScannerInfo(buildConfig, commandLine, buildDirectory, tmpFile);
		} catch (IOException e) {
			Activator.log(e);
			return null;
		}
	}

	@Override
	public IExtendedScannerInfo getDefaultScannerInfo(IBuildConfiguration buildConfig,
			IExtendedScannerInfo baseScannerInfo, ILanguage language, URI buildDirectoryURI) {
		try {
			String[] commands = getCompileCommands(language);
			if (commands == null || commands.length == 0) {
				// no default commands
				return null;
			}

			Path buildDirectory = Paths.get(buildDirectoryURI);

			// Pick the first one
			Path command = Paths.get(commands[0]);
			List<String> commandLine = new ArrayList<>();
			if (command.isAbsolute()) {
				commandLine.add(command.toString());
			} else {
				commandLine.add(getCommandPath(command).toString());
			}

			if (baseScannerInfo != null && baseScannerInfo.getIncludePaths() != null) {
				for (String includePath : baseScannerInfo.getIncludePaths()) {
					commandLine.add("-I" + includePath); //$NON-NLS-1$
				}
			}

			addDiscoveryOptions(commandLine);

			// output to stdout
			commandLine.add("-o"); //$NON-NLS-1$
			commandLine.add("-"); //$NON-NLS-1$

			// Source is an empty tmp file
			String extension;
			if (GPPLanguage.ID.equals(language.getId())) {
				extension = ".cpp"; //$NON-NLS-1$
			} else if (GCCLanguage.ID.equals(language.getId())) {
				extension = ".c"; //$NON-NLS-1$
			} else {
				// In theory we shouldn't get here
				return null;
			}

			Path tmpFile = Files.createTempFile(buildDirectory, ".sc", extension); //$NON-NLS-1$
			commandLine.add(tmpFile.toString());

			return getScannerInfo(buildConfig, commandLine, buildDirectory, tmpFile);
		} catch (IOException e) {
			Activator.log(e);
			return null;
		}
	}

	private IExtendedScannerInfo getScannerInfo(IBuildConfiguration buildConfig, List<String> commandLine,
			Path buildDirectory, Path tmpFile) throws IOException {
		Files.createDirectories(buildDirectory);

		// Startup the command
		ProcessBuilder processBuilder = new ProcessBuilder(commandLine).directory(buildDirectory.toFile())
				.redirectErrorStream(true);
		CCorePlugin.getDefault().getBuildEnvironmentManager().setEnvironment(processBuilder.environment(),
				buildConfig, true);
		Process process = processBuilder.start();

		// Scan for the scanner info
		Map<String, String> symbols = new HashMap<>();
		List<String> includePath = new ArrayList<>();
		Pattern definePattern = Pattern.compile("#define (.*)\\s(.*)"); //$NON-NLS-1$
		boolean inIncludePaths = false;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (inIncludePaths) {
					if (line.equals("End of search list.")) { //$NON-NLS-1$
						inIncludePaths = false;
					} else {
						includePath.add(line.trim());
					}
				} else if (line.startsWith("#define ")) { //$NON-NLS-1$
					Matcher matcher = definePattern.matcher(line);
					if (matcher.matches()) {
						symbols.put(matcher.group(1), matcher.group(2));
					}
				} else if (line.equals("#include <...> search starts here:")) { //$NON-NLS-1$
					inIncludePaths = true;
				}
			}
		}

		try {
			process.waitFor();
		} catch (InterruptedException e) {
			Activator.log(e);
		}
		Files.delete(tmpFile);

		return new ExtendedScannerInfo(symbols, includePath.toArray(new String[includePath.size()]));

	}

	@Override
	public String[] getErrorParserIds() {
		return new String[] { "org.eclipse.cdt.core.GCCErrorParser", //$NON-NLS-1$
				"org.eclipse.cdt.core.GASErrorParser", //$NON-NLS-1$
				"org.eclipse.cdt.core.GLDErrorParser", //$NON-NLS-1$
				"org.eclipse.cdt.core.GmakeErrorParser", //$NON-NLS-1$
				"org.eclipse.cdt.core.CWDLocator" //$NON-NLS-1$
		};
	}

	@Override
	public IEnvironmentVariable getVariable(String name) {
		if (pathVar != null && (name.equals("PATH") || name.equals("Path"))) { //$NON-NLS-1$ //$NON-NLS-2$
			return pathVar;
		}
		return null;
	}

	@Override
	public IEnvironmentVariable[] getVariables() {
		return envVars;
	}

	@Override
	public Path getCommandPath(Path command) {
		if (command.isAbsolute()) {
			return command;
		}

		if (Platform.getOS().equals(Platform.OS_WIN32)) {
			if (!command.toString().endsWith(".exe") && !command.toString().endsWith(".bat")) { //$NON-NLS-1$ //$NON-NLS-2$
				command = Paths.get(command.toString() + ".exe"); //$NON-NLS-1$
			}
		}

		// Look for it in the path environment var
		IEnvironmentVariable myPath = getVariable("PATH"); //$NON-NLS-1$
		String path = myPath != null ? myPath.getValue() : System.getenv("PATH"); //$NON-NLS-1$
		for (String entry : path.split(File.pathSeparator)) {
			Path entryPath = Paths.get(entry);
			Path cmdPath = entryPath.resolve(command);
			if (Files.isExecutable(cmdPath)) {
				return cmdPath;
			}
		}

		return null;
	}

	private void initCompileCommands() {
		if (commands == null) {
			cCommand = path.getFileName().toString();
			cppCommand = null;
			if (cCommand.contains("gcc")) { //$NON-NLS-1$
				cppCommand = cCommand.replace("gcc", "g++"); //$NON-NLS-1$ //$NON-NLS-2$
				commands = new String[] { cCommand, cppCommand };
			} else if (cCommand.contains("clang")) { //$NON-NLS-1$
				cppCommand = cCommand.replace("clang", "clang++"); //$NON-NLS-1$ //$NON-NLS-2$
				commands = new String[] { cCommand, cppCommand };
			} else {
				commands = new String[] { cCommand };
			}
		}
	}

	@Override
	public String[] getCompileCommands() {
		initCompileCommands();
		return commands;
	}

	@Override
	public String[] getCompileCommands(ILanguage language) {
		initCompileCommands();
		if (GPPLanguage.ID.equals(language.getId())) {
			return new String[] { cppCommand != null ? cppCommand : cCommand };
		} else if (GCCLanguage.ID.equals(language.getId())) {
			return new String[] { cCommand };
		} else {
			return new String[0];
		}
	}

	@Override
	public IResource[] getResourcesFromCommand(List<String> cmd, URI buildDirectoryURI) {
		// Start at the back looking for arguments
		List<IResource> resources = new ArrayList<>();
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		for (int i = cmd.size() - 1; i >= 0; --i) {
			String arg = cmd.get(i);
			if (arg.startsWith("-")) { //$NON-NLS-1$
				// ran into an option, we're done.
				break;
			}
			Path srcPath = Paths.get(arg);
			URI uri;
			if (srcPath.isAbsolute()) {
				uri = srcPath.toUri();
			} else {
				try {
					uri = buildDirectoryURI.resolve(arg);
				} catch (IllegalArgumentException e) {
					// Bad URI
					continue;
				}
			}

			for (IFile resource : root.findFilesForLocationURI(uri)) {
				resources.add(resource);
			}
		}

		return resources.toArray(new IResource[resources.size()]);
	}

	@Override
	public List<String> stripCommand(List<String> command, IResource[] resources) {
		List<String> newCommand = new ArrayList<>();

		for (int i = 0; i < command.size() - resources.length; ++i) {
			String arg = command.get(i);
			if (arg.startsWith("-o")) { //$NON-NLS-1$
				if (arg.equals("-o")) { //$NON-NLS-1$
					i++;
				}
				continue;
			}
			newCommand.add(arg);
		}

		return newCommand;
	}

	public static class GCCInfo {
		private static final Pattern versionPattern = Pattern.compile(".*(gcc|LLVM) version .*"); //$NON-NLS-1$
		private static final Pattern targetPattern = Pattern.compile("Target: (.*)"); //$NON-NLS-1$

		public String target;
		public String version;

		public GCCInfo(String command) throws IOException {
			this(command, null);
		}

		public GCCInfo(String command, Map<String, String> env) throws IOException {
			ProcessBuilder builder = new ProcessBuilder(new String[] { command, "-v" }).redirectErrorStream(true); //$NON-NLS-1$
			if (env != null) {
				Map<String, String> procEnv = builder.environment();
				for (Entry<String, String> entry : env.entrySet()) {
					if ("PATH".equals(entry.getKey())) { //$NON-NLS-1$
						// prepend the path
						String path = entry.getValue() + File.pathSeparator + procEnv.get("PATH"); //$NON-NLS-1$
						procEnv.put("PATH", path); //$NON-NLS-1$
					} else {
						// replace
						procEnv.put(entry.getKey(), entry.getValue());
					}
				}
			}
			Process proc = builder.start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
				for (String line = reader.readLine(); line != null; line = reader.readLine()) {
					Matcher versionMatcher = versionPattern.matcher(line);
					if (versionMatcher.matches()) {
						version = line.trim();
						continue;
					}
					Matcher targetMatcher = targetPattern.matcher(line);
					if (targetMatcher.matches()) {
						target = targetMatcher.group(1);
						continue;
					}
				}
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			GCCInfo other = (GCCInfo) obj;
			if (target == null) {
				if (other.target != null)
					return false;
			} else if (!target.equals(other.target))
				return false;
			if (version == null) {
				if (other.version != null)
					return false;
			} else if (!version.equals(other.version))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((target == null) ? 0 : target.hashCode());
			result = prime * result + ((version == null) ? 0 : version.hashCode());
			return result;
		}

	}
}
