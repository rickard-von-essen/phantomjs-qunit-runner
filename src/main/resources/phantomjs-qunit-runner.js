// Credit to Rod of http://whileonefork.blogspot.com/2011/07/javascript-unit-tests-with-qunit-ant.html for inspiration of this js file
String.prototype.supplant = function(o) {
	return this.replace(/{([^{}]*)}/g, function(a, b) {
		var r = o[b];
		return typeof r === 'string' || typeof r === 'number' ? r : a;
	});
};

var JUnitXmlFormatter = {
	someProperty : 'some value here',
	printJUnitXmlOutputHeader : function(testsErrors, testsTotal,
			testsTotalRunTime, testsFailures, testsFileName) {
		console.log("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
		console
				.log("<testsuite errors=\"{_testsErrors}\" tests=\"{_testsTotal}\" time=\"{_testsTotalRunTime}\" failures=\"{_testsFailures}\" name=\"{_testsFileName}\">"
						.supplant({
							_testsErrors : testsErrors,
							_testsTotal : testsTotal,
							_testsTotalRunTime : testsTotalRunTime,
							_testsFailures : testsFailures,
							_testsFileName : testsFileName
						}));
	},
	printJUnitXmlTestCasePass : function(testObject, testName, testRunTime) {
		console.log("<testcase time=\"{_testRunTime}\" name=\"{_testName}\"/>"
				.supplant({
					_testRunTime : testRunTime,
					_testName : testName
				}));
	},
	printJUnitXmlTestCaseFail : function(testObject, testName, testRunTime, failureType,
			failureMessage) {
		console
				.log("XXX<testcase time=\"{_testRunTime}\" name=\"{_testName}\">"
						.supplant({
							_testRunTime : testRunTime,
							_testName : testName
						}));
		console
				.log("<failure type=\"{_failureType}\" message=\"{_failureMessage}\">"
						.supplant({
							_failureType : failureType,
							_failureMessage : failureMessage
						}));
		console.log("PhantomJS QUnit failure on test : '{_testName}'"
				.supplant({
					_testName : testName
				}));
		var assertMsgAdded = false;
		var i;
		for( i = 0 ; i < logQueue.length ; ++i ) {
			e = logQueue[i];
			if( !e.result ) {
				if( !assertMsgAdded ) {
					console.log( ", failed assertions: ");
					assertMsgAdded = true;
				} else
					console.log( ", ");
				console.log( "'"+e.message+"'");
			}
		}
		console.log("</failure>");
		console.log("</testcase>");
	},
	printJUnitXmlOutputFooter : function() {
		console.log("</testsuite>");
	}
};

function importJs(scriptName) {
	phantom.injectJs(scriptName);
}



//Arg1 should be Phantomjs QUnit wrapper
importJs(phantom.args[0]);

//Arg2 should be QUnit
importJs(phantom.args[1]);

// Arg3 should be user tests
var usrTestScript = phantom.args[2];
importJs(usrTestScript);

// Arg4 should be user tests
var usrSrcScript = phantom.args[3];
importJs(usrSrcScript);

//Arg5 should be DOM Test helper util
importJs(phantom.args[4]);

//Arg6 should be jQuery
importJs(phantom.args[5]);

// Run QUnit
var testsPassed = 0;
var testsFailed = 0;
var testStartDate;
var testEndDate;
var testRunTime;
var totalRunTime = 0;

// extend copied from QUnit.js
function extend(a, b) {
	for ( var prop in b) {
		if (b[prop] === undefined) {
			delete a[prop];
		} else {
			a[prop] = b[prop];
		}
	}

	return a;
}
JUnitXmlFormatter.printJUnitXmlOutputHeader(0, testsPassed + testsFailed,
		totalRunTime, testsFailed, usrTestScript);
QUnit.begin({});

// Initialize the config, saving the execution queue
var oldconfig = extend({}, QUnit.config);
QUnit.init();
extend(QUnit.config, oldconfig);
var logQueue = [];

QUnit.log = function(details) {
	logQueue.push(details);
}

QUnit.testStart = function(t) {
	testStartDate = new Date();
	logQueue = [];
}

QUnit.testDone = function(t) {
	testEndDate = new Date();
	testRunTime = testEndDate.getTime() - testStartDate.getTime();
	totalRunTime = parseInt(totalRunTime) + parseInt(testRunTime);

	if (0 === t.failed) {
		testsPassed++;
		JUnitXmlFormatter.printJUnitXmlTestCasePass(t, t.name, testRunTime);
	} else {
		testsFailed++;
		JUnitXmlFormatter.printJUnitXmlTestCaseFail(t, t.name, testRunTime, 1, 1);
	}
}

// Test timeout for asynchronous tests in secs
var testTimeOut = 1;
// Length of one tick count in msec when checking the
// status of async tests
var tickLen = 50;		// in msecs
var tickCounter = 0;

var running = true;
QUnit.done = function(i) {
	running = false;
}

var started = 0;

QUnit.start = function() {
	++started;
}

QUnit.stop = function() {
	--started;
}

function processQueue() {
	while (QUnit.config.queue.length && ( started >= 0 ) )
		QUnit.config.queue.shift()();
	return QUnit.config.queue.length == 0;
}

function processAndWait() {
	if( processQueue() ) {
		JUnitXmlFormatter.printJUnitXmlOutputFooter();
		phantom.exit(testsFailed);
	} else {
		tickCounter = testTimeOut * 1000 / tickLen;
		setTimeout( waitAndContinue,50 );
	}
}

function waitAndContinue() {
	if( started >= 0 )
		processAndWait();
	else {
		--tickCounter;
		if( tickCounter <= 0 ) {
			QUnit.ok(false,"test timeout" );
			++started;
			processAndWait();
		} else
			setTimeout( waitAndContinue,50 );
	}
}


// Instead of QUnit.start(); just directly exec; the timer stuff seems to
// invariably screw us up and we don't need it
QUnit.config.semaphore = 0;
processAndWait();
