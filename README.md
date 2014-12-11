VASCO
=====

VASCO is a framework for performing precise inter-procedural data flow analysis using VAlue Sensitive COntexts.

The framework classes are present in the package `vasco` and are described in the paper: [Interprocedural Data Flow Analysis in Soot using Value Contexts](http://dl.acm.org/citation.cfm?doid=2487568.2487569), which was presented in the SOAP 2013 workshop (co-located with PLDI 2013 at Seattle, Washington).

You can use these classes directly with any program analysis toolkit or intermediate representation, although they work best with [Soot](http://www.sable.mcgill.ca/soot) and it's *Jimple* representation.

This framework was developed as a part of a larger M.Tech. project on precise heap alias analysis by [Rohan Padhye](http://www.cse.iitb.ac.in/~rohanpadhye) under the supervision of [Prof. Uday Khedker](http://www.cse.iitb.ac.in/~uday) at the [Indian Institute of Technology Bombay](http://www.iitb.ac.in) ([Department of Computer Science and Engineering](http://www.cse.iitb.ac.in/alumni/~rohanpadhye11).

## API Documentation ##

There is a JavaDoc generated [API documentation](http://rohanpadhye.github.io/vasco/apidocs) available for the framework classes.

## Points-to Analysis ##

A sample use of this framework can be found in the package `vasco.callgraph` which contains a points-to analysis that builds a context-sensitive call graph on-the-fly.

### Experiments ###

To execute the tests as described in the paper, ensure that Soot is in your class path and run:

<code>
java vasco.callgraph.CallGraphTest [-cp CLASSPATH] [-out DIR] [-k DEPTH] MAIN_CLASS
</code>

Where:

- `CLASSPATH` is used to locate application classes of the program to analyze (default: `.`)
- `DIR` is the output directory where results will be dumped (default: `.`)
- `DEPTH` is the maximum depth of call chains to count (default: `10`)
- `MAIN_CLASS` is the entry point to the program

This will generate a bunch of CSV files in the output directory containing statistics of analyzed methods, contexts, and call chains.

### Using Generated Call Graphs ###

To use the generated call graphs progmatically in your own Soot-based analysis, make sure to add the `vasco.callgraph.CallGraphTransformer` to Soot's analysis `PackManager` (see code in `CallGraphTest.main()` for how to do this) which set's the call-graphs in the global `Scene` appropriately.

Any Soot-based analysis that you add to the pipe-line after the `CallGraphTransformer` can make use of the generated call-graphs by calling `Scene.v().getCallGraph()` or `Scene.v().getContextSensitiveCallGraph()` as you would with the results of SPARK and PADDLE respectively.

## Other Examples ##

The package `vasco.soot.examples` contains some example analyses implemented for Soot such as **copy constant propagation** and a simple **sign analysis**.

## Limitations ##

Although the implementation of VASCO captures all the theory presented in the SOAP'13 paper, it still requires work to make it useful in the real world.

The following is a list of limitations of this framework, which need to be addressed. **Any help (suggestions or code) in addressing these issues would be greatly appreciated**.

- Native methods are not handled by the `DefaultJimpleRepresentation`. How to approximate the effect of methods whose bodies are not available for analysis is an open question and probably depends on the nature of the analysis.
- The JRE is not modelled. For example, `System.in` and `System.out` have initial values of `null` in the source code of `java.lang.System`. The JVM injects values for standard input/output streams at runtime. Other such JVM behaviours are not correctly modelled.
- Multi-threaded programs are not currently supported. Although some frameworks choose to solve this by simply linking calls to to `Thread.start()` with the approriate `Runnable.run` method, the resulting analysis may not be sound if it does not consider interleavings of program execution. Perhaps this is a responsibility of the analysis to handle, and not the framework. If so, the linking needs to be implementated for VASCO to support multi-threaded programs.
- Reflection and/or dynamic class loading is not modelled. If your candidate program has this, you probably need to use some tool such as TamiFlex to resolve dynamic bindings and transform your code to use static bindings instead before feeding it to VASCO for static analysis.

Again, if anybody is willing to help with any of the above issues, please do come forwards. Code can be submitted via pull requests, and suggestions can be sent via email (contact details are usually on [Rohan Padhye's homepage](http://www.cse.iitb.ac.in/alumni/~rohanpadhye11/)).
