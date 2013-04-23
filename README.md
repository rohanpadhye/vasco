VASCO
=====

VASCO is a framework for performing precise inter-procedural data flow analysis using VAlue Sensitive COntexts.

The framework classes are present in the package `vasco` and are described in the paper: *Link to PDF will be added soon.*

You can use these classes directly with any program analysis toolkit or intermediate representation, although they work best with [Soot](http://www.sable.mcgill.ca/soot) and it's *Jimple* representation.

## API Documentation ##

The API documentation for the framework classes is available at http://rohanpadhye.github.io/vasco/apidocs.

## Points-to Analysis ##

A sample use of this framework can be found in the package `vasco.callgraph` which contains a points-to analysis that builds a context-sensitive call graph on-the-fly.

### Usage ###

To test this implementation ensure that Soot is in your class path and run:

<code>
java vasco.callgraph.Test [-cp CLASSPATH] [-out DIR] [-k DEPTH] MAIN_CLASS
</code>

Where:

- `CLASSPATH` is used to locate application classes of the program to analyze (default: `.`)
- `DIR` is the output directory where results will be dumped (default: `.`)
- `DEPTH` is the maximum depth of call chains to count (default: `10`)
- `MAIN_CLASS` is the entry point to the program

## Pending Tasks ##

### Points-to analysis: ###

- Construct Soot-friendly results for the constructed call-graph (`soot.jimple.toolkits.callgraph.CallGraph` and `soot.jimple.toolkits.callgraph.ContextSensitiveCallGraph`).
- Handle Thread.start() calls to support multi-threaded programs.

### Inter-procedural framework: ###

- Improve performance using multi-threaded processing of flow functions.
