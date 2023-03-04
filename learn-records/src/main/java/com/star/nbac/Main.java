package com.star.nbac;

import java.util.Arrays;

/**
 * NBAC = Nested Based Access Control
 *
 * @author Starry
 * @since 03-04-2023
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // JDK11 后引入 NBAC 反射调用不报错
        // new Outer().outerPublic();

        /**
         * Inner 的嵌套宿主: com.star.nbac.Outer | Inner NestMembers: [class com.star.nbac.Outer, class com.star.nbac.Outer$Inner]
         * Outer 的嵌套宿主: com.star.nbac.Outer | Outer NestMembers: [class com.star.nbac.Outer, class com.star.nbac.Outer$Inner]
         */
        System.out.println("Inner 的嵌套宿主: " + Outer.Inner.class.getNestHost().getName() +
                " | Inner NestMembers: " + Arrays.toString(Outer.Inner.class.getNestMembers()));
        System.out.println("Outer 的嵌套宿主: " + Outer.class.getNestHost().getName() +
                " | Outer NestMembers: " + Arrays.toString(Outer.class.getNestMembers()));
    }

}
