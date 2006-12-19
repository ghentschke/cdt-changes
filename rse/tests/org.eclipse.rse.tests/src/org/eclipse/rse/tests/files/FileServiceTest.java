/*******************************************************************************
 * Copyright (c) 2006 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Martin Oberhuber (Wind River) - initial API and implementation
 *******************************************************************************/
package org.eclipse.rse.tests.files;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.model.ISystemRegistry;
import org.eclipse.rse.core.subsystems.ISubSystem;
import org.eclipse.rse.model.SystemStartHere;
import org.eclipse.rse.services.clientserver.messages.SystemMessageException;
import org.eclipse.rse.services.files.IFileService;
import org.eclipse.rse.services.files.IHostFile;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.tests.core.connection.RSEBaseConnectionTestCase;

public class FileServiceTest extends RSEBaseConnectionTestCase {

	private IFileServiceSubSystem fss;
	private IFileService fs;
	private File tempDir;
	private String tempDirPath;
	private IProgressMonitor mon = new NullProgressMonitor();
	
	public void setUp() {
		IHost localHost = getLocalSystemConnection();
		ISystemRegistry sr = SystemStartHere.getSystemRegistry(); 
		ISubSystem[] ss = sr.getServiceSubSystems(IFileService.class, localHost);
		for (int i=0; i<ss.length; i++) {
			if (ss[i] instanceof IFileServiceSubSystem) {
				fss = (IFileServiceSubSystem)ss[i];
				fs = fss.getFileService();
			}
		}
		try {
			 tempDir = File.createTempFile("rsetest","dir"); //$NON-NLS-1$ //$NON-NLS-2$
			 assertTrue(tempDir.delete());
			 assertTrue(tempDir.mkdir());
			 tempDirPath = tempDir.getAbsolutePath();
		} catch(IOException ioe) {
			assertTrue("Exception creating temp dir", false); //$NON-NLS-1$
		}
	}
	
	public void tearDown() {
		try {
			fs.delete(mon, tempDir.getParent(), tempDir.getName());
		} catch(SystemMessageException msg) {
			assertFalse("Exception: "+msg.getLocalizedMessage(), true); //$NON-NLS-1$
		}
	}
	
	public boolean isWindows() {
		String systemTypeId = fss.getHost().getSystemType(); 
		if (systemTypeId.equals("Local")) { //$NON-NLS-1$
			return System.getProperty("os.name").toLowerCase().startsWith("win"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return systemTypeId.toLowerCase().startsWith("windows"); //$NON-NLS-1$
	}
	
	public String getTestFileName() {
		//Return a filename for testing that exposes all characters valid on the file system
		if (!isWindows()) {
			//UNIX
			return "a !@#${a}\"\' file%^&*()?_ =[]+-';:,.|<>"; //$NON-NLS-1$
		}
		//Fallback: Windows
		return "a !@#${a}' file%^&()_ =[]+-;,."; //$NON-NLS-1$
	}
	
	public void testCaseSensitive() {
		if (isWindows()) {
			assertFalse(fss.getSubSystemConfiguration().isCaseSensitive());
			assertFalse(fss.isCaseSensitive());
			assertFalse(fs.isCaseSensitive()); //FAIL due to bug 168586
		} else {
			assertTrue(fss.getSubSystemConfiguration().isCaseSensitive());
			assertTrue(fss.isCaseSensitive());
			assertTrue(fs.isCaseSensitive());
		}
	}
	
	public void testCreateFile() throws SystemMessageException {
		String testName = getTestFileName();
		IHostFile hf = fs.createFile(mon, tempDirPath, testName);
		assertTrue(hf.exists());
		assertTrue(hf.canRead());
		assertTrue(hf.canWrite());
		assertEquals(hf.getName(), testName);
		
		File theFile = new File(tempDir, testName); 
		assertTrue(theFile.exists());
	}
	
	public void testCreateCaseSensitive() throws SystemMessageException {
		String testName = getTestFileName();
		String testName2 = testName.toUpperCase();
		IHostFile hf = fs.createFile(mon, tempDirPath, testName);
		if (fss.isCaseSensitive()) {
			//UNIX: uppercase version must be distinct
			IHostFile hf2 = fs.getFile(mon, tempDirPath, testName2);
			assertFalse(hf2.exists());
			hf2 = fs.createFolder(mon, tempDirPath, testName2);
			assertTrue(hf2.exists());
			assertTrue(hf2.isDirectory());
		} else {
			//Windows: uppercase version must be the same
			IHostFile hf2 = fs.getFile(mon, tempDirPath, testName2);
			assertTrue(hf2.exists());
			try {
				hf2 = fs.createFolder(mon, tempDirPath, testName2);
			} catch(SystemMessageException e) {
				assertNotNull(e);
			}
			assertTrue(hf2.exists());
			assertFalse(hf2.isDirectory());
			assertEquals(hf.getModifiedDate(), hf2.getModifiedDate());
			assertEquals(hf.getSize(), hf2.getSize());
		}
	}

}
