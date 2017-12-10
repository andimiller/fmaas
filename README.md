# flatMap as a Service

## Motivation

What if I had a framework that let me easily spin up new projects which...

1. Take input from a queue
2. Transform the data
3. Put it into another queue

and

1. serves an admin endpoint to say if it's doing okay
2. lets the user register extra commands
3. logs sensibly
4. cleanly shuts down when told to