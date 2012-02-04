package net.kennychua.phantomjs_qunit_runner;

/*
 * This is not the cleanest code you will ever see....
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

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
	 * @parameter expression="${project.build.directory}"
	 */
	private File buildDirectory;

	/**
	 * Boolean to fail build on failure
	 * 
	 * @parameter expression="${maven.test.failure.ignore}" default-value=false
	 */
	private boolean ignoreFailures;

	/**
	 * Optional command to invoke phantomJs executable (can be space delimited commands).
	 * 
	 * eg. 'xvfb-run -a /usr/bin/phantomjs'
	 * 
	 * If not set, then defaults to assuming the 'phantomjs' executable is in system path.
	 * 
	 * @parameter expression="${phantomjs.exec}" default-value="phantomjs"
	 */
	private String phantomJsExec;

	/**
	 * Filenames of JS test files from the jsTestDirectory to exclude.
	 * 
	 * @parameter alias="excludes";
	 */
	private String[] mExcludes;
	// XXX Add excludes logic

	private static final String jQueryFileName = "jquery-1.7.1-min.js";
	private static final String domTestUtilsFileName = "DOMTestUtils.js";
	private static final String qUnitJsFileName = "qunit-git.js";
	private static final String phantomJsQunitRunner = "phantomjs-qunit-runner.js";
	private static final String jsTestFileSuffix = "Test.js";
	private static final String jUnitXmlDirectoryName = "junitxml";

	public void setExcludes(String[] excludes) {
		mExcludes = excludes;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		int retCode = 0;
		
		getLog().debug("jsTestDirectory=" + String.valueOf(jsTestDirectory));
		getLog().debug("jsSourceDirectory=" + String.valueOf(jsSourceDirectory));
		getLog().debug("phantomJsExec=" + String.valueOf(phantomJsExec));
		

		// Go over all the js test files in jsTestDirectory
		for (File temp : getJsTestFiles(jsTestDirectory.toString())) {
			// Run each through phantomJs to test
			retCode += runQUnitInPhantomJs(temp.getName().toString(),
					jsTestDirectory.toString());
		}

		if (!ignoreFailures) {
			// If ever retCode is more than 1, it means error(s) have occurred
			if (retCode > 0) {
				throw new MojoFailureException("One or more QUnit tests failed");
			}
		}
	}

	private int runQUnitInPhantomJs(String testFile, String testFileDirectory) {
		int exitVal = 255;
		
		getLog().debug("testFile=" + testFile);
		getLog().debug("testFileDirectory=" + testFileDirectory);
		
		try {
			// Set parameters
			// needs to be : phantomjs phantomjsqunitrunner qunit.js AbcTest.js
			// Abc.js
			// Abc.js
			String[] phantomJsExecArgs = phantomJsExec.split(" ");
			ArrayList<String> paramsList = new ArrayList<String>();
			for (String arg : phantomJsExecArgs) {
				paramsList.add(arg);
			}
			
			// XXX todo : unix executable. how to store and pull down from
			// nexus?

			// Copy js phantomjs-qunit-runner plugin over for use..
			// - phantomJsQunitRunner  	- wrapper so QUnit tests can run in PhantomJs
			// - qUnitJsFileName		- QUnit Test framework
			// - jQueryFileName			- jQuery library
			// - domTestUtilsFileName	- DOM setup and teardown helper functions for DOM assert testings 
			
			try {
				FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream(phantomJsQunitRunner), new File(buildDirectory + "/" + phantomJsQunitRunner));
				FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream(qUnitJsFileName),new File(buildDirectory + "/" + qUnitJsFileName));
				FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream(jQueryFileName),new File(buildDirectory + "/" + jQueryFileName));
				FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream(domTestUtilsFileName),new File(buildDirectory + "/" + domTestUtilsFileName));
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Set further params for the previously copied files
			paramsList.add(buildDirectory + "/" + domTestUtilsFileName);
			paramsList.add(buildDirectory + "/" + jQueryFileName);
			paramsList.add(buildDirectory + "/" + phantomJsQunitRunner);
			paramsList.add(buildDirectory + "/" + qUnitJsFileName);
			paramsList.add(testFileDirectory + "/" + testFile);
			
			// Some dirty string manipulation here to resolve js src file
			paramsList.add(jsSourceDirectory + "/"
					+ testFile.substring(0, testFile.indexOf(jsTestFileSuffix))
					+ ".js");

			getLog().debug("params passed to process = " + paramsList.toString());
			Process pr = new ProcessBuilder(paramsList).start();

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

			exitVal = pr.waitFor();
			
		} catch (Exception e) {
			getLog().error(e);
		}
		return exitVal;
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
