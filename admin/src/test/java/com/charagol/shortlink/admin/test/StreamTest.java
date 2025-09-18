package com.charagol.shortlink.admin.test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamTest {

    public static void main(String[] args) {

        System.out.println("========================= Stream Creation ===================");
        // NEW! 这部分为Stream流的创建方法
        Stream<String> stream = Stream.of("Java", "Python", "Go");

        Optional<String> o = stream.filter(s -> s.contains("o")).findFirst();
        o.ifPresent(System.out::println);

        String value = "Not Null";
        Stream<String> stream1 = Stream.ofNullable(value);
        stream1.forEach(System.out::println);


        Stream<String> emptyStream = Stream.empty();
        emptyStream.forEach(System.out::println);

        Stream.iterate(0, n -> n < 10, n -> n + 2).forEach(System.out::println);


        Stream.generate(Math::random)
                .limit(3)
                .forEach(System.out::println);


        Stream<String> streamA = Stream.of("a", "b");
        Stream<String> streamB = Stream.of("c", "d");
        Stream<String> combined = Stream.concat(streamA, streamB); // 结果是 a, b, c, d
        combined.forEach(System.out::println);

        System.out.println("========================= Stream Collectors ===================");

        // NEW! 这部分为Stream流中的常用收集器使用方法
        List<String> list = Arrays.asList("小张","张三", "李四", "王五","王老吉","赵六");

        List<String> s1 = list.stream().filter(s -> s.contains("张")).toList();
        System.out.println(s1);
        s1.forEach(System.out::println);

        String s2 = list.stream().limit(3).collect(Collectors.joining(","));
        System.out.println(s2);

        Object[] s3 = list.stream().filter(s -> s.startsWith("王")).toArray();
        System.out.println(Arrays.toString(s3));

        String[] array = list.stream().filter(s -> s.startsWith("王")).toArray(String[]::new);
        System.out.println(Arrays.toString(array));

        System.out.println("==================== Optional ====================");

        // NEW! 这部分为Optional类中的方法使用方法
        Optional<String> optional = Optional.of("Hello");
        String world = optional.orElse("World");// 如果optional有值，返回该值，否则返回World
        System.out.println(world);
    }
}
