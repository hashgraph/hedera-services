package jasperdb.virtualdatasource;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class DatabaseMergingState extends DatabaseState {

    @Param({"true"})
    public boolean mergingEnabled;
}
