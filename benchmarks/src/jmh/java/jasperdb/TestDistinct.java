package jasperdb;

import org.eclipse.collections.impl.block.factory.HashingStrategies;
import org.eclipse.collections.impl.utility.ListIterate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Try and find the best way to do a distinct operation on a stream
 */
public class TestDistinct {
    public static void main(String[] args) {
        List<Person> list = Arrays.asList(
                new Person(1,"Jasper 1"),
                new Person(2,"Richard"),
                new Person(1,"Jasper 2"),
                new Person(3,"Albert")
        );
        System.out.println("list = " + Arrays.toString(list.toArray()));
        var list2 = list.stream()
                .distinct()
                .collect(Collectors.toList());
        System.out.println("list2 = " + Arrays.toString(list2.toArray()));
        var list3 = list.stream()
                .filter(distinctByKey(Person::getId))
                .collect(Collectors.toList());
        System.out.println("list3 = " + Arrays.toString(list3.toArray()));

        List<Person> list4 = ListIterate
                .distinct(list, HashingStrategies.fromFunction(Person::getId));
        System.out.println("list4 = " + Arrays.toString(list4.toArray()));


        var list5 = list.stream()
                .sorted(Comparator.comparingLong(Person::getId))
                .collect(Collectors.toList());
        System.out.println("list5 = " + Arrays.toString(list5.toArray()));

        var list6 = list.stream()
                .sorted(Comparator.comparingLong(Person::getId))
                .filter(new DistinctByIdOrdered())
                .collect(Collectors.toList());
        System.out.println("list6 = " + Arrays.toString(list6.toArray()));
        var list7 = list.stream()
                .parallel()
                .sorted(Comparator.comparingLong(Person::getId))
                .filter(new DistinctByIdOrdered())
                .collect(Collectors.toList());
        System.out.println("list7 = " + Arrays.toString(list7.toArray()));

        List<VersionedPerson> listVersioned = Arrays.asList(
                new VersionedPerson(1,1,"Jasper 1"),
                new VersionedPerson(2,1,"Richard 1"),
                new VersionedPerson(1,2,"Jasper 2"),
                new VersionedPerson(3,2,"Albert 2"),
                new VersionedPerson(3,1,"Albert 1"),
                new VersionedPerson(2,3,"Richard 3")
        );
        var list8 = listVersioned.stream()
                .sorted(Comparator.comparingLong(VersionedPerson::getId))
                .filter(new DistinctByIdOrdered())
                .collect(Collectors.toList());
        System.out.println("list8 = " + Arrays.toString(list8.toArray()));
        var list9 = listVersioned.stream()
                .parallel()
                .sorted(Comparator.comparingLong(VersionedPerson::getId))
                .filter(new DistinctByIdOrdered())
                .collect(Collectors.toList());
        System.out.println("list9 = " + Arrays.toString(list9.toArray()));
        var list10 = listVersioned.stream()
                .sorted(Comparator.comparingLong(VersionedPerson::getId).thenComparingLong(VersionedPerson::getVersion))
                .filter(new DistinctByIdOrdered())
                .collect(Collectors.toList());
        System.out.println("list10 = " + Arrays.toString(list10.toArray()));
    }

    public static <T> Predicate<T> distinctByKey(
            Function<? super T, ?> keyExtractor) {

        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    public static class DistinctByIdOrdered implements Predicate<Person> {
        private Person last = null;
        @Override
        public boolean test(Person p) {
            final boolean result = last == null ||  last.getId() != p.getId();
            last = p;
            return result;
        }
    }

    public static class Person {
        public final long id;
        public final String name;

        public Person(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Person person = (Person) o;
            return id == person.id && Objects.equals(name, person.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }

        @Override
        public String toString() {
            return "Person{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    public static class VersionedPerson extends Person {
        public final long version;

        public VersionedPerson(long id, long version, String name) {
            super(id,name);
            this.version = version;
        }

        public long getVersion() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VersionedPerson that = (VersionedPerson) o;
            return id == that.id && version == that.version && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, version, name);
        }

        @Override
        public String toString() {
            return "VP{" +
                    "id=" + id +
                    ", version=" + version +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}
