commensal-ejml
==============

commensal-ejml is a commensal compiler for the [EJML matrix math
library](https://github.com/lessthanoptimal/ejml).  This is a
pedagogical example, not a production-quality compiler; I've
deliberately restricted its scope to one benchmark (the Kalman filter
included with EJML).

To learn more about commensal compliation, see [StreamJIT: A Commensal
Compiler for High-Performance Stream Programming](http://groups.csail.mit.edu/commit/papers/2014/bosboom-oopsla14-commensal.pdf),
published in OOPSLA 2014.  (This compiler is not described in the paper,
but was featured in the talk [(slides)](http://groups.csail.mit.edu/commit/papers/2014/bosboom-oopsla14-commensal-slides.pdf).)

Building
--------

`ant fetch; ant jar`

Running the benchmark
---------------------

`ant run -Dimpl={simple,ops,commensal}`

Running under ant or an IDE seems to inject noise into this benchmark,
so for best results, run with `java` directly.
