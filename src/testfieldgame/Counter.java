package testfieldgame;

/**
 * A simple counter containing int value, which can be controlled via 
 * increment, decrement, set and get.
 * It is Not Thread Safe at all. 
 * <p>
 * In other classes I use just one final object of Counter to coordinate 
 * threads in a pool. All the usage is inside of synchronized (jobsCounter) blocks.
 * @author Vladislav Ustinov
 */
public final class Counter {

    private int val;

    public Counter(int val) {
        this.val = val;
    }

    public void increment() {
        val++;
        Game.safePrintln("jobsCount after incr = " + val);
    }

    public void decrement() {
        val--;
        Game.safePrintln("jobsCount after decr = " + val);
    }
    
    public void set (int val){
        this.val = val;
        Game.safePrintln("jobsCount after set = " + val);
    }
    
    public int get (){
        Game.safePrintln("jobsCount in get = " + val);
        return val;
        
    }
}

//new44

