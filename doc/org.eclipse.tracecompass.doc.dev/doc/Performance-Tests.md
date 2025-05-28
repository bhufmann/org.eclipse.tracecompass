# Performance Tests

Performance testing allows to calculate some metrics (CPU time, Memory
Usage, etc.) that some part of the code takes during its execution.
These metrics can then be used as is for information on the system's
execution, or they can be compared either with other execution
scenarios, or previous runs of the same scenario, for instance, after
some optimization has been done on the code.

For automatic performance metric computation, we use the
*org.eclipse.test.performance* plugin, provided by the Eclipse Test
Feature.

## Add performance tests

### Where

Performance tests are unit tests and they are added to the corresponding
unit tests plugin. To separate performance tests from unit tests, a
separate source folder, typically named *perf*, is added to the plug-in.

Tests are to be added to a package under the *perf* directory, the
package name would typically match the name of the package it is
testing. For each package, a class named **AllPerfTests** would list all
the performance tests classes inside this package. And like for unit
tests, a class named **AllPerfTests** for the plug-in would list all the
packages' **AllPerfTests** classes.

When adding performance tests for the first time in a plug-in, the
plug-in's **AllPerfTests** class should be added to the global list of
performance tests, found in package *org.eclipse.tracecompass.alltests*,
in class **RunAllPerfTests**. This will ensure that performance tests
for the plug-in are run along with the other performance tests

### How

TMF is using the org.eclipse.test.performance framework for performance
tests. Using this, performance metrics are automatically taken and, if
many runs of the tests are run, average and standard deviation are
automatically computed. Results can optionally be stored to a database
for later use.

Here is an example of how to use the test framework in a performance
test:

```java
    public class AnalysisBenchmark {

        private static final String TEST_ID = "org.eclipse.linuxtools#LTTng kernel analysis";
        private static final CtfTmfTestTrace testTrace = CtfTmfTestTrace.TRACE2;
        private static final int LOOP_COUNT = 10;

        /**
         * Performance test
         */
        @Test
        public void testTrace() {
            assumeTrue(testTrace.exists());

            /** Create a new performance meter for this scenario */
            Performance perf = Performance.getDefault();
            PerformanceMeter pm = perf.createPerformanceMeter(TEST_ID);

            /** Optionally, tag this test for summary or global summary on a given dimension */
            perf.tagAsSummary(pm, "LTTng Kernel Analysis", Dimension.CPU_TIME);
            perf.tagAsGlobalSummary(pm, "LTTng Kernel Analysis", Dimension.CPU_TIME);

            /** The test will be run LOOP_COUNT times */
            for (int i = 0; i < LOOP_COUNT; i++) {

                /** Start each run of the test with new objects to avoid different code paths */
                try (IAnalysisModule module = new KernelAnalysis();
                        LttngKernelTrace trace = new LttngKernelTrace()) {
                    module.setId("test");
                    trace.initTrace(null, testTrace.getPath(), CtfTmfEvent.class);
                    module.setTrace(trace);

                    /** The analysis execution is being tested, so performance metrics
                     * are taken before and after the execution */
                    pm.start();
                    TmfTestHelper.executeAnalysis(module);
                    pm.stop();

                    /*
                     * Delete the supplementary files, so next iteration rebuilds
                     * the state system.
                     */
                    File suppDir = new File(TmfTraceManager.getSupplementaryFileDir(trace));
                    for (File file : suppDir.listFiles()) {
                        file.delete();
                    }

                } catch (TmfAnalysisException | TmfTraceException e) {
                    fail(e.getMessage());
                }
            }

            /** Once the test has been run many times, committing the results will
             * calculate average, standard deviation, and, if configured, save the
             * data to a database */
            pm.commit();
        }
    }
```

For more information, see [The Eclipse Performance Test
How-to](http://wiki.eclipse.org/Performance/Automated_Tests)

Some rules to help write performance tests are explained in section
[ABC of performance testing](#abc-of-performance-testing)

### Run a performance test

Performance tests are unit tests, so, just like unit tests, they can be
run by right-clicking on a performance test class and selecting *Run As*
-\> *Junit Plug-in Test*.

By default, if no database has been configured, results will be
displayed in the Console at the end of the test.

Here is the sample output from the test described in the previous
section. It shows all the metrics that have been calculated during the
test.

    Scenario 'org.eclipse.linuxtools#LTTng kernel analysis' (average over 10 samples):
      System Time:            3.04s         (95% in [2.77s, 3.3s])         Measurable effect: 464ms (1.3 SDs) (required sample size for an effect of 5% of mean: 94)
      Used Java Heap:        -1.43M         (95% in [-33.67M, 30.81M])     Measurable effect: 57.01M (1.3 SDs) (required sample size for an effect of 5% of stdev: 6401)
      Working Set:           14.43M         (95% in [-966.01K, 29.81M])    Measurable effect: 27.19M (1.3 SDs) (required sample size for an effect of 5% of stdev: 6400)
      Elapsed Process:        3.04s         (95% in [2.77s, 3.3s])         Measurable effect: 464ms (1.3 SDs) (required sample size for an effect of 5% of mean: 94)
      Kernel time:             621ms        (95% in [586ms, 655ms])        Measurable effect: 60ms (1.3 SDs) (required sample size for an effect of 5% of mean: 39)
      CPU Time:               6.06s         (95% in [5.02s, 7.09s])        Measurable effect: 1.83s (1.3 SDs) (required sample size for an effect of 5% of mean: 365)
      Hard Page Faults:          0          (95% in [0, 0])                Measurable effect: 0 (1.3 SDs) (required sample size for an effect of 5% of stdev: 6400)
      Soft Page Faults:       9.27K         (95% in [3.28K, 15.27K])       Measurable effect: 10.6K (1.3 SDs) (required sample size for an effect of 5% of mean: 5224)
      Text Size:                 0          (95% in [0, 0])
      Data Size:                 0          (95% in [0, 0])
      Library Size:           32.5M         (95% in [-12.69M, 77.69M])     Measurable effect: 79.91M (1.3 SDs) (required sample size for an effect of 5% of stdev: 6401)

Results from performance tests can be saved automatically to a derby
database. Derby can be run either in embedded mode, locally on a
machine, or on a server. More information on setting up derby for
performance tests can be found here: [The Eclipse Performance Test
How-to](http://wiki.eclipse.org/Performance/Automated_Tests). The
following documentation will show how to configure an Eclipse run
configuration to store results on a derby database located on a server.

Note that to store results in a derby database, the *org.apache.derby*
plug-in must be available within your Eclipse. Since it is an optional
dependency, it is not included in the target definition. It can be
installed via the **Orbit** repository, in *Help* -> *Install new
software...*. If the **Orbit** repository is not listed, click on the
latest one from [1](http://download.eclipse.org/tools/orbit/downloads/)
and copy the link under *Orbit Build Repository*.

To store the data to a database, it needs to be configured in the run
configuration. In *Run* -> *Run configurations..*, under *Junit Plug-in
Test*, find the run configuration that corresponds to the test you wish
to run, or create one if it is not present yet.

In the *Arguments* tab, in the box under *VM Arguments*, add on separate
lines the following information

```
    -Declipse.perf.dbloc=//javaderby.dorsal.polymtl.ca
    -Declipse.perf.config=build=mybuild;host=myhost;config=linux;jvm=1.7
```

The *eclipse.perf.dbloc* parameter is the url (or filename) of the derby
database. The database is by default named *perfDB*, with username and
password *guest*/*guest*. If the database does not exist, it will be
created, initialized and populated.

The *eclipse.perf.config* parameter identifies a **variation**: It
typically identifies the build on which is it run (commitId and/or build
date, etc.), the machine (host) on which it is run, the configuration of
the system (for example Linux or Windows), the jvm, etc. That parameter
is a list of ';' separated key-value pairs. To be backward-compatible
with the Eclipse Performance Tests Framework, the 4 keys mentioned above
are mandatory, but any key-value pairs can be used.

## ABC of performance testing

Here follow some rules to help design good and meaningful performance
tests.

### Determine what to test

For tests to be significant, it is important to choose what exactly is
to be tested and make sure it is reproducible every run. To limit the
amount of noise caused by the TMF framework, the performance test code
should be tweaked so that only the method under test is run. For
instance, a trace should not be "opened" (by calling the *traceOpened()*
method) to test an analysis, since the *traceOpened* method will also
trigger the indexing and the execution of all applicable automatic
analysis.

For each code path to test, multiple scenarios can be defined. For
instance, an analysis could be run on different traces, with different
sizes. The results will show how the system scales and/or varies
depending on the objects it is executed on.

The number of **samples** used to compute the results is also important.
The code to test will typically be inside a **for** loop that runs
exactly the same code each time for a given number of times. All objects
used for the test must start in the same state at each iteration of the
loop. For instance, any trace used during an execution should be
disposed of at the end of the loop, and any supplementary file that may
have been generated in the run should be deleted.

Before submitting a performance test to the code review, you should run
it a few times (with results in the Console) and see if the standard
deviation is not too large and if the results are reproducible.

### Metrics descriptions and considerations

CPU time: CPU time represent the total time spent on CPU by the current
process, for the time of the test execution. It is the sum of the time
spent by all threads. On one hand, it is more significant than the
elapsed time, since it should be the same no matter how many CPU cores
the computer has. But since it calculates the time of every thread, one
has to make sure that only threads related to what is being tested are
executed during that time, or else the results will include the times of
those other threads. For an application like TMF, it is hard to control
all the threads, and empirically, it is found to vary a lot more than
the system time from one run to the other.

System time (Elapsed time): The time between the start and the end of
the execution. It will vary depending on the parallelization of the
threads and the load of the machine.

Kernel time: Time spent in kernel mode

Used Java Heap: It is the difference between the memory used at the
beginning of the execution and at the end. This metric may be useful to
calculate the overall size occupied by the data generated by the test
run, by forcing a garbage collection before taking the metrics at the
beginning and at the end of the execution. But it will not show the
memory used throughout the execution. There can be a large standard
deviation. The reason for this is that when benchmarking methods that
trigger tasks in different threads, like signals and/or analysis, these
other threads might be in various states at each run of the test, which
will impact the memory usage calculated. When using this metric, either
make sure the method to test does not trigger external threads or make
sure you wait for them to finish.
