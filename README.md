# Java-Concurrency-Hello-World-Life-Game
This is some exercise after reading Java Concurrency in Practice. I used BlockingQueue, pool of Threads, wait-notify, etc. Threads are acting together in a simplified Life Game.

See java-doc for more details about classes. 


 The project is dedicated only to training in Java Concurrency. 
 The goal was to make several threads having personal pieces of data and sending/receiving 
 messages via BlockingQueue. Not to be bored, I emagined a game similar to famous game Life, but much simpler.
 
 Game is main class in the project. 
 Its main goal is to perform a game loop with digits moving across the game field (2d array) according to some rules.
 The rules are set in FieldQuadrant#applyRule function. 
 For now, the general rule is just to move each nonzero digit to the left in the game field.
 
 About the game and the threads. Suppose, we have a 2d array, most of which values are zeros, 
 except for a few values equal to one (call them "ones"). 
 Lets divide the field into smaller 2d square arrays (quadrants), on which some threads will act.
 The threads should move "ones" to the left each turn of the game. 
 When any "one" reaches left boundary of its threads' field part, 
 it should jump to the next threads' field part. 
 If it was boundary of the most left quadrant, the "one" should jump to the most right quadrant.
 So we have global periodic boundary condition in our field.
 
 The threads are FieldQuadrant objects. 
 Threads' pool is formed simply via storing them in array Game#fieldQuadrantAr.
 Each turn of the game is performed inside of loop in Game#start function 
 with fixed number of iterations equal to Game#NUM_ITERATIONS.
 
 Each turn we first set up integer inside of jobsCount equal to NUM_THREADS. 
 This means that threads have to perform at least NUM_THREADS jobs. 
 Indeed, each thread should at least move its "ones" inside of its part of the global field (FieldQuadrants#fieldQuad).
 Then, if some of the "ones" got to the left boundary, the threads put corresponding messages into 
 the message queues of other threads ("ones" jump to other field parts). 
 Before putting new message about new job, the thread increases jobsCount.
 Then, the thread checks up its own message queue, 
 takes messages and places new "ones" in its part of the field, if any. 
 After doing another job, the thread decreases jobsCount.
 The thread will wait in msgQueue.take() eternally until the main thread will interrupt it.
 
 The main thread in Game#start function waits until other threads from the pool notify 
 that jobsCount has been changed. When jobsCount is zero, 
 the main thread understands that there are no more jobs to be done and
 interrupts all the other threads in the pool. Then we're done and next turn comes.
 
 See GameTest class for launching the productivity test of Game class. 
 After fixed amount of iterations the game field should become the same as it was in the beginning.
 This follows from our periodic boundary condition, and this is used to check up for games expected vs actual state in the test.
 
 @author Vladislav Ustinov
 @version 1.0
 
