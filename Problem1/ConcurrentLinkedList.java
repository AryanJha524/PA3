/*
   This code references the Lock-Free list implementation in
   The Art of Multiprocessor Programming 13th Edition, Chapter 9, Section 8
 */

import java.io.*;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.*;


public class ConcurrentLinkedList<T> {
Node head;
public ConcurrentLinkedList() {
        this.head = new Node(Integer.MIN_VALUE);
        Node tail = new Node(Integer.MAX_VALUE);
        while (!head.next.compareAndSet(null, tail, false, false));
}

// function that returns true if given data is added
public boolean add(T data) {
        // get key for this value
        int key = data.hashCode();
        while (true) {
                Window w = find(head, key);
                Node pred = w.pred;
                Node curr = w.curr;
                if (curr.key == key) {
                        // this item already exists in list
                        // no need to add it
                        return false;
                }
                else {
                        // create a new node and link it into the chain
                        Node newNode = new Node(data);
                        newNode.next = new AtomicMarkableReference(curr, false);
                        // set pred to new node (success only if pred is unmarked
                        // and if it refers to curr)
                        if (pred.next.compareAndSet(curr, newNode, false, false)) {
                                return true;
                        }
                }
        }
}

// function that returns true if given data is removed from the list
public boolean remove(T data) {
        int key = data.hashCode();
        boolean snip;
        while (true) {
                Window w = find(head, key);
                Node pred = w.pred;
                Node curr = w.curr;
                // return false if this node is not in list
                if (curr.key != key) {
                        return false;
                }
                else {
                        // grab the reference to the next node of the current node
                        Node succ = curr.next.getReference();
                        // expect current node's next to still be the succ node
                        snip = curr.next.compareAndSet(succ, succ, false, false);
                        // if this isn't true, try again
                        if (!snip) {
                                continue;
                        }
                        // set pred next to curr's successor node
                        pred.next.compareAndSet(curr, succ, false, false);
                        return true;
                }
        }
}

// function that returns whether or not a given id is present in list
public boolean contains(T data) {
        boolean[] marked = {false};
        int key = data.hashCode();
        Node curr = head;
        // loop until we reach the node we are searching for
        while (curr.key < key) {
                // traverse curr node
                curr = curr.next.getReference();
                // test whether or not current node is marked
                Node succ = curr.next.get(marked);
        }
        return (curr.key == key && !marked[0]);
}


class Node {
T data;
int key;
AtomicMarkableReference<Node> next;
public Node(T data) {
        this.data = data;
        this.key = data.hashCode();
        this.next = new AtomicMarkableReference<Node>(null, false);
}

public Node(int key) {
        this.data = null;
        this.key = key;
        this.next = new AtomicMarkableReference<Node>(null, false);
}
}


class Window {
Node pred;
Node curr;
public Window(Node p, Node c) {
        this.pred = p;
        this.curr = c;
}
}

// removes logically marked nodes + returns object
// with nodes on either side of key (prev and next)
public Window find(Node head, int key) {
        Node pred = null, curr = null, succ = null;
        boolean marked[] = {false};
        boolean snip;
        retry : while (true) {
                // traverse through list
                pred = head;
                curr = pred.next.getReference();
                while (true) {
                        // set succ to current's next reference
                        // also retrieve current's mark (store in array)
                        succ = curr.next.get(marked);
                        while (marked[0]) {
                                snip = pred.next.compareAndSet(curr, succ, false, false);
                                if (!snip) {
                                        continue retry;
                                }
                                curr = succ;
                                // ensure that curr is not logically removed
                                succ = curr.next.get(marked);
                        }
                        if (curr.key >= key) {
                                return new Window(pred, curr);
                        }
                        pred = curr;
                        curr = succ;
                }
        }
}

public static void main(String args[]) {
        System.out.println("please use the ConcurrentListDriver class!");
}
}
