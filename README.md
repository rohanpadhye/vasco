VASCO
=====

VASCO is a framework for performing precise inter-procedural data flow analysis using VAlue Sensitive COntexts.

The framework classes are present in the package `vasco` and are described in the paper: [Interprocedural Data Flow Analysis in Soot using Value Contexts](http://arxiv.org/abs/1304.6274)

You can use these classes directly with any program analysis toolkit or intermediate representation, although they work best with [Soot](http://www.sable.mcgill.ca/soot) and it's *Jimple* representation.

This framework was developed as a part of a larger M.Tech. project on precise heap alias analysis by [Rohan Padhye](http://www.cse.iitb.ac.in/~rohanpadhye) under the supervision of [Prof. Uday Khedker](http://www.cse.iitb.ac.in/~uday) at the [Indian Institute of Technology Bombay](http://www.iitb.ac.in) ([Department of Computer Science and Engineering](http://www.cse.iitb.ac.in)).

## API Documentation ##

There is a JavaDoc generated [API documentation](http://rohanpadhye.github.io/vasco/apidocs) available for the framework classes.

## Points-to Analysis ##

A sample use of this framework can be found in the package `vasco.callgraph` which contains a points-to analysis that builds a context-sensitive call graph on-the-fly.

### Experiments ###

To execute the tests as described in the paper, ensure that Soot is in your class path and run:

<code>
java vasco.callgraph.Test [-cp CLASSPATH] [-out DIR] [-k DEPTH] MAIN_CLASS
</code>

Where:

- `CLASSPATH` is used to locate application classes of the program to analyze (default: `.`)
- `DIR` is the output directory where results will be dumped (default: `.`)
- `DEPTH` is the maximum depth of call chains to count (default: `10`)
- `MAIN_CLASS` is the entry point to the program

This will generate a bunch of CSV files in the output directory containing statistics of analyzed methods, contexts, and call chains.

## Pending Tasks ##

### Points-to analysis: ###

- Construct Soot-friendly results for the constructed call-graph (`soot.jimple.toolkits.callgraph.CallGraph` and `soot.jimple.toolkits.callgraph.ContextSensitiveCallGraph`).
- Handle Thread.start() calls to support multi-threaded programs.

### Inter-procedural framework: ###

- Improve performance using multi-threaded processing of flow functions.
- Add a simpler example (e.g. null dereference analysis) as points-to analysis may be too complex to serve as a tutorial
