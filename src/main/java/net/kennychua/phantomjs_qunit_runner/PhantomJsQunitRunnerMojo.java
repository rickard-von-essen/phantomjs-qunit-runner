package net.kennychua.phantomjs_qunit_runner;

/*
 * This is not the cleanest code you will ever see....
 */
import java.io.*;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

/**
 * Goal which runs QUnit tests in PhantomJs (by convention)
 * 
 * @goal test
 * 
 * @phase test
 */
public class PhantomJsQunitRunnerMojo extends AbstractMojo {
	/**
	 * Directory of JS src files to be tested.
	 * 
	 * @parameter expression="${qunit.jssrc.directory}"
	 * @required
	 */
	private File jsSourceDirectory;

	/**
	 * Directory of JS test files.
	 * 
	 * @parameter expression="${qunit.jstest.directory}"
	 * @required
	 */
	private File jsTestDirectory;

	/**
	 * Base directory of project/
	 * 
	 * @parameter expression="${basedir}"
	 */
	private File baseDirectory;

	/**
	 * Directory containing the build files
	 * 
	 * @parameter expression="${project.build.directory}/qunit"
	 */
	private File buildDirectory;

	/**
	 * Boolean to fail build on failure
	 * 
	 * @parameter expression="${maven.test.failure.ignore}" default-value=false
	 */
	private boolean ignoreFailures;

	/**
	 * Optional command to invoke phantomJs executable (can be space delimited
	 * commands).
	 * 
	 * eg. 'xvfb-run -a /usr/bin/phantomjs'
	 * 
	 * If not set, then defaults to assuming the 'phantomjs' executable is in
	 * system path.
	 * 
	 * @parameter expression="${phantomjs.exec}" default-value="phantomjs"
	 */
	private String phantomJsExec;

	/**
	 * Optional parameter to add additional js libraries to test-run. This
	 * allows you to use things like a mocking framework...
	 * 
	 * @parameter
	 */
	private FileSet includeLibsInDir;

	private static final String[] qunitLibraries = new String[] {
			"phantomjs-qunit-runner.js", // - wrapper so QUnit tests can run in
											// PhantomJs
			"qunit-git.js" // - QUnit Test framework
	};
	private static final String[] tailLibraries = new String[] {
			"jquery-1.7.1-min.js", // - jQuery library
			"DOMTestUtils.js" // - DOM setup and teardown helper functions for
								// DOM assert testings
	};

	private static final String jsTestFileSuffix = "Test.js";
	private static final String jUnitXmlDirectoryName = "junitxml";

	private static FileSetManager fileSetManager = new FileSetManager();

	private String createStringArrayLogString(String[] fileNames) {
		String logString = new String();
		if (fileNames != null) {
			for (String libFile : fileNames) {
				logString = logString + libFile;
				logString = logString + ", ";
			}
		}
		return logString;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		int retCode = 0;

		printConfigurationToLog();

		copyFilesToTargetDirectory();

		// Go over all the js test files in jsTestDirectory
		for (File jsTestFile : getJsTestFiles(jsTestDirectory.getAbsolutePath())) {
			retCode += runQUnitInPhantomJs(jsTestFile.getName(),
					jsTestDirectory.getAbsolutePath());
		}

		if (!ignoreFailures) {
			// If ever retCode is more than 1, it means error(s) have occurred
			if (retCode > 0) {
				throw new MojoFailureException("One or more QUnit tests failed");
			}
		}
	}

	private void copyFilesToTargetDirectory() {
		copyLibraries(qunitLibraries);
		copyLibraries(tailLibraries);
		copyUserDefinedLibraries();
	}

	private void printConfigurationToLog() {
		getLog().debug("jsTestDirectory=" + String.valueOf(jsTestDirectory));
		getLog().debug("jsSourceDirectory=" + String.valueOf(jsSourceDirectory));
		getLog().debug("phantomJsExec=" + String.valueOf(phantomJsExec));

		if (includeLibsInDir != null) {
			String[] fileNames = fileSetManager.getIncludedFiles(includeLibsInDir);
			getLog().debug("libraries=" + createStringArrayLogString(fileNames));
		} else {
			getLog().debug("libraries=none");
		}
	}

	private int runQUnitInPhantomJs(String testFile, String testFileDirectory) {
		int exitVal = 255;

		getLog().debug("testFile=" + testFile);
		getLog().debug("testFileDirectory=" + testFileDirectory);

		try {
			ArrayList<String> paramsList = new ArrayList<String>();

			paramsList.addAll(convertPhantomJsExecToParameterList());
			paramsList.addAll(createFileList(buildDirectory.getAbsolutePath(),
					qunitLibraries));

			paramsList.add(testFileDirectory + "/" + testFile);
			paramsList.add(findFileUnderTest(testFile));

			paramsList.addAll(createFileList(buildDirectory.getAbsolutePath(),
					tailLibraries));
			if (includeLibsInDir != null) {
				paramsList.addAll(createFileList(
						buildDirectory.getAbsolutePath(),
						fileSetManager.getIncludedFiles(includeLibsInDir)));
			}

			getLog().debug(
					"params passed to process = " + paramsList.toString());

			Process pr = new ProcessBuilder(paramsList).start();
			captureOutput(testFile, pr);
			exitVal = pr.waitFor();

		} catch (Exception e) {
			getLog().error(e);
		}
		return exitVal;
	}

	/**
	 * Some dirty string manipulation here to resolve js src file
	 * 
	 * @param testFile
	 */
	private String findFileUnderTest(String testFile) {
		return jsSourceDirectory + "/"
				+ testFile.substring(0, testFile.indexOf(jsTestFileSuffix))
				+ ".js";
	}

	private void captureOutput(String testFile, Process pr) throws IOException {
		// Grab STDOUT of execution (this is the junit xml output generated
		// by the js), write to file
		BufferedReader input = new BufferedReader(new InputStreamReader(
				pr.getInputStream()));
		File jUnitXmlOutputPath = new File(buildDirectory + "/"
				+ jUnitXmlDirectoryName);

		jUnitXmlOutputPath.mkdir();
		File resultsFile = new File(jUnitXmlOutputPath, testFile + ".xml");

		// Write out the stdout from phantomjs to the junit xml file.
		BufferedWriter output = new BufferedWriter(new FileWriter(resultsFile));
		String line = null;
		while ((line = input.readLine()) != null) {
			output.write(line);
		}
		output.close();
	}

	private ArrayList<String> convertPhantomJsExecToParameterList() {
		ArrayList<String> phantomJsParams = new ArrayList<String>();
		for (String arg : phantomJsExec.split(" ")) {
			phantomJsParams.add(arg);
		}
		return phantomJsParams;
	}

	/**
	 * Create an ArrayList of Strings that contains all given filenames, with
	 * the given directory prefixed.
	 */
	private ArrayList<String> createFileList(String directory,
			String[] filenames) {
		ArrayList<String> prefixedFiles = new ArrayList<String>();
		for (String filename : filenames) {
			prefixedFiles.add(directory + "/" + filename);
		}
		return prefixedFiles;
	}

	/**
	 * Copy all library files to output directory, so that they're available to
	 * phantomjs.
	 */
	private void copyUserDefinedLibraries() {
		if (includeLibsInDir != null) {
			for (String libraryFileName : fileSetManager
					.getIncludedFiles(includeLibsInDir)) {
				try {
					File libraryFile = new File(includeLibsInDir.getDirectory()
							+ libraryFileName);
					FileUtils.copyFile(libraryFile, new File(buildDirectory
							+ "/" + libraryFile.getName()));
				} catch (IOException e) {
					getLog().error(e);
				}
			}
		}
	}

	/**
	 * Copy all the core libraries used by phantomjs-qunit-runner
	 */
	private void copyLibraries(String[] filenames) {
		for (String filename : filenames) {
			copyResourceToDirectory(filename, buildDirectory);
		}
	}

	private void copyResourceToDirectory(String filename, File buildDirectory) {
		try {
			FileUtils.copyInputStreamToFile(getFileAsStream(filename),
					new File(buildDirectory + "/" + filename));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private InputStream getFileAsStream(String filename) {
		return this.getClass().getClassLoader().getResourceAsStream(filename);
	}

	private File[] getJsTestFiles(String dirName) {
		File dir = new File(dirName);

		return dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				return filename.endsWith(jsTestFileSuffix);
			}
		});
	}
}
