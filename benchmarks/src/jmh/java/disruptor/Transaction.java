package disruptor;

public class Transaction<T> {
    T data;

    boolean last;

    public Transaction() { }

    public T getData() {
        return data;
    }

    public void setData(T data) { this.data = data; }

    public boolean isLast() { return last; }

    public void setLast(boolean last) { this.last = last; }

    public void clear() {
        this.data = null;
        this.last = false;
    }
}