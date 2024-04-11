package com.example.libreriamm.motiondetector;

import android.util.Log;

public class ArrayQueue {
    private int front;
    private final int rear;
    private final int capacity;
    private final float[] queue;

    public ArrayQueue(int c) {
        front = rear = c - 1;
        capacity = c;
        queue = new float[capacity];
    }

    // function to insert an element
    // at the rear of the queue
    public void queueEnqueue(float data) {
        // check queue is full or not
        if (0 == front) {

            queueDequeue();
            queue[capacity - 1] = data;
            // System.out.printf("\nQueue is full\n");
            return;

        } else {

            // insert element at the rear
            for (int i = 0; i < capacity - 1; i++) {
                queue[i] = queue[i + 1];
            }
            queue[capacity - 1] = data;
            front--;

            return;
        }
    }

    // function to delete an element
    // from the front of the queue
    public void queueDequeue() {
        // if queue is empty
        if (front == capacity - 1) {
            //System.out.printf("\nQueue is empty\n");
            return;
        }

        // shift all the elements from index 2 till rear
        // to the right by one
        else {
            for (int i = 0; i < capacity - 1; i++) {
                queue[i] = queue[i + 1];
            }

            // store 0 at rear indicating there's no element
            //if (front < capacity)
            queue[capacity - 1] = 0;

            // decrement rear
            //front--;
        }
        return;
    }

    // print queue elements
    public void queueDisplay() {
        int i;
        if (front == rear) {
            Log.d("BUFFER", "\nQueue is Empty\n");
            return;
        }

        String conc = "ARRAYQUEUE --> ";

        // traverse front to rear and print elements
        for (i = front; i < rear; i++) {
            conc = conc.concat(" | " + queue[i]);
        }
        Log.d("BUFFER", conc);


        return;
    }

    // print front of queue
    public void queueFront() {
        if (front == rear) {
            System.out.printf("\nQueue is Empty\n");
            return;
        }
        System.out.printf("\nFront Element is: %d", queue[front]);
        return;
    }

    public float[] getQueue() {
        return queue;
    }
}