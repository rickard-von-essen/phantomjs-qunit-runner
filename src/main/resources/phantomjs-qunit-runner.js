//Arg1 should be Phantomjs QUnit wrapper
//Arg2 should be QUnit
//Arg3 should be user tests
//Arg4 should be user tests
//Arg5 should be DOM Test helper util
//Arg6 should be jQuery

phantom.exit((function(args){
	for(var i = 0; i < args.length; ++i) {
		phantom.injectJs(args[i]);
	}

	var props = {
		qunit			: args[0],
		test			: args[1],
		source		: args[2],
		DOMHelper	: args[3],
		jQuery		: args[4]
	};

	var tests = [];
	var fails = [];
	var suites = [];
	var module = {
		name: props.source,
		start: new Date()
	}
	var testStart = new Date();
	totalFails = 0;

	var closeModule = function(vals) {
		var now = new Date();
		vals.ts = now;
		vals.time = now.getTime() - vals.start.getTime();
		vals.failed = fails.length;
		vals.total = tests.length;
		suites.push(suiteXml(vals, tests.join('')));
		tests = [];
		fails = [];
	}

	QUnit.moduleStart = function(context) {
		if ( tests.length ) {
			closeModule(module);
		}
		module.name = context.name;
		module.start = new Date();
	};

	QUnit.moduleDone = function(context) {
		context.start = module.start;
		closeModule(context);
		module.start = new Date();
	};

	QUnit.testStart = function(context) {
		testStart = new Date();
	};

	QUnit.log = function(context) {
		if ( !context.result ) {
			fails.push(failXml(context));
			totalFails++;
		}	
	};

	QUnit.testDone = function(context) {
		context.time = new Date() - testStart;
		if ( !context.module ) {
			context.module = module.name;
		}
		tests.push(testXml(context,fails.join('')));
	};

	QUnit.done = function(context) {
		running = false;
	};

	var reportXml = function(props,suites) {
		console.log('<?xml version="1.0" encoding="UTF-8" ?>');
		console.log('<testsuites name="'+props.source+'">');
		console.log(propsXml(props));
		console.log(suites);
		console.log('</testsuites>');
	};
	
	var propsXml = function(props) {
		var r = [];
		for (var key in props) {
		  if (props.hasOwnProperty(key)) {
				r.push('<property name="'+key+'" value="'+props[key]+'" />\n');
		  }
		}
		return '<properties>\n'+r.join('')+'</properties>';
	};

	var suiteXml = function(vals, tests) {
		var r = [];
		r.push('<testsuite');
		//r.push(' errors="'+errors+'"');
		r.push(' failures="'+vals.failed+'"');
		//r.push(' hostname=""');
		r.push(' name="'+vals.name+'"');
		r.push(' tests="'+vals.total+'"');
		r.push(' time="'+vals.time+'"');
		r.push(' timestamp="'+vals.ts+'">\n');
		r.push(tests);
		r.push('</testsuite>\n');
		return r.join('');
	};

	var testXml = function(vars,fails) {
		var r = [];
		if ( fails.length ) {
			r.push('<testcase');
			r.push(' classname="'+vars.module+'"');
			r.push(' name="'+vars.name+'"');
			r.push(' time="'+vars.time+'">\n');
			r.push(fails);
			r.push('</testcase>\n');
		} else {
			r.push('<testcase');
			r.push(' classname="'+vars.module+'" ');
			r.push(' name="'+vars.name+'" ');
			r.push(' time="'+vars.time+'" />\n');
		}
		return r.join('');
	};

	var failXml = function(vars) {
		var type = 'phantomjs-qunit-runner';
		var msg = entitize(vars.message);
		var r = [];
		msg += ':  expected &lt;'+vars.expected+'&gt;, but was: &lt;'+vars.actual+'&gt;';
		r.push('<failure message="'+msg+'" type="'+type+'">');
		r.push(type+': '+msg);
		r.push('</failure>\n');
		return r.join('');
	};

	var entitize = function(str) {
		return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
	}

	function extend(a, b) {
	  for ( var prop in b) {
	    if (b[prop] === undefined) {
	      delete a[prop];
	    } else {
	      a[prop] = b[prop];
	    }
	  }
		return a;
	};

	QUnit.begin({});
	var oldconfig = extend({}, QUnit.config);
	QUnit.init();
	extend(QUnit.config, oldconfig);

	var running = true;
	testStart = new Date();
	module.start = new Date();
	
	QUnit.config.semaphore = 0;
	while (QUnit.config.queue.length){
	  QUnit.config.queue.shift()();
	}
	var ct = 0;
	while (running) {
	  if (ct++ % 1000000 == 0) {
	  }
	  if (!QUnit.config.queue.length) {
	    QUnit.done();
	  }
	}

	if ( tests.length ) {
		closeModule(module);
	}

	reportXml(props,suites.join(''));
	
	return totalFails;

})(phantom.args));

