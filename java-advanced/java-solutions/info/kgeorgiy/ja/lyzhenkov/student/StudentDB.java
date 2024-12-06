package info.kgeorgiy.ja.lyzhenkov.student;

import info.kgeorgiy.java.advanced.student.Group;
import info.kgeorgiy.java.advanced.student.GroupName;
import info.kgeorgiy.java.advanced.student.GroupQuery;
import info.kgeorgiy.java.advanced.student.Student;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StudentDB implements GroupQuery {

    private static final Comparator<Student> COMPARATOR_BY_NAME = Comparator
            .comparing(Student::getLastName)
            .thenComparing(Student::getFirstName)
            .thenComparing(Comparator.comparing(Student::getId).reversed());

    private static <T, R> List<R> mapStream(
            Collection<? extends T> collection,
            Function<? super T, R> mapper
    ) {
        return collection.stream().map(mapper).toList();
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return mapStream(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return mapStream(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return mapStream(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return mapStream(students, s -> s.getFirstName() + " " + s.getLastName());
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return new TreeSet<>(getFirstNames(students));
    }

    private static <T> Stream<T> sortStream(Collection<T> collection, Comparator<? super T> comp) {
        return collection.stream().sorted(comp);
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return sortStream(students, Comparator.reverseOrder())
                .map(Student::getFirstName)
                .findFirst()
                .orElse("");
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortStream(students, Student::compareTo).toList();
    }

    private static <T> List<T> filterAndSort(
            Collection<T> collection,
            Predicate<? super T> predicate,
            Comparator<? super T> comp
    ) {
        return collection.stream()
                .filter(predicate)
                .sorted(comp)
                .toList();
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortStream(students, COMPARATOR_BY_NAME).toList();
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return filterAndSort(students, student -> student.getFirstName().equals(name), COMPARATOR_BY_NAME);
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return filterAndSort(students, student -> student.getLastName().equals(name), COMPARATOR_BY_NAME);
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return filterAndSort(students, student -> student.getGroup().equals(group), COMPARATOR_BY_NAME);
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return findStudentsByGroup(students, group).stream()
                .collect(Collectors.toMap(
                        Student::getLastName,
                        Student::getFirstName,
                        BinaryOperator.minBy(Comparator.naturalOrder())
                ));
    }

    private static <T, K, V> List<Map.Entry<K, V>> collectDataToMap(
            Collection<? extends T> collection,
            Collector<? super T, ?, Map<K, V>> collector
    ) {
        return collection.stream()
                .collect(collector)
                .entrySet()
                .stream()
                .toList();
    }

    private static List<Group> getGroupsBy(Collection<Student> collection, Comparator<? super Student> comp) {
        return mapStream(
                sortStream(
                        collectDataToMap(
                                collection,
                                Collectors.groupingBy(Student::getGroup)
                        ),
                        Map.Entry.comparingByKey()
                ).toList(),
                pair -> new Group(pair.getKey(), sortStream(pair.getValue(), comp).toList())
        );
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getGroupsBy(students, COMPARATOR_BY_NAME);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getGroupsBy(students, Student::compareTo);
    }

    private static <U, K extends Comparable<? super K>> K getLargest(
            Collection<? extends Student> collection,
            Function<? super Student, K> keyFunc,
            Function<? super Student, U> valueFunc,
            Comparator<? super Map.Entry<K, Integer>> comp,
            K defaultValue
    ) {

        return collectDataToMap(collection, Collectors.groupingBy(
                keyFunc, Collectors.collectingAndThen(
                        Collectors.mapping(
                                valueFunc,
                                Collectors.toUnmodifiableSet()
                        ),
                        Set::size
                )))
                .stream()
                .max(Map.Entry.<K, Integer>comparingByValue().thenComparing(comp))
                .map(Map.Entry::getKey)
                .orElse(defaultValue);
    }

    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getLargest(students, Student::getGroup, Function.identity(),
                Map.Entry.comparingByKey(), null);
    }

    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getLargest(students, Student::getGroup,
                Student::getFirstName, Map.Entry.<GroupName, Integer>comparingByKey().reversed(), null);
    }
}
