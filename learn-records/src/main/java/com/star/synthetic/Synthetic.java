package com.star.synthetic;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author Starry
 * @since 03-04-2023
 */
public class Synthetic {

    /**
     * JDK 11 之前
     * Synthetic 关键字 由 Java 编译器在编译阶段自动生成的构造
     * JLS: 所有存在于字节码文件中，但是不存在于源代码文件中的构造，都应该被 Synthetic 标注
     * 构造 => Constructs => Field, Method, Constructor
     * 相当于 JS 里的 var that = this;
     * JDK11 之后，access$ 已不需要
     */

    private String field1 = "1";
    private String field2 = "2";

    public String getField1() {
        return field1;
    }

    public void setField1(String field1) {
        this.field1 = field1;
    }

    public String getField2() {
        return field2;
    }

    public void setField2(String field2) {
        this.field2 = field2;
    }

    private void outerMethod() {
        System.out.println("this is outerMethod");
    }

    public InnerClass c = new InnerClass();

    private class InnerClass {
        private String subField1 = "1";
        private String subField2 = "2";

        public String getSubField1() {
            return subField1;
        }

        public void setSubField1(String subField1) {
            this.subField1 = subField1;
        }

        public String getSubField2() {
            return subField2;
        }

        public void setSubField2(String subField2) {
            this.subField2 = subField2;
        }

        // private Synthetic this$0; 内部类持有一个外部类的实例

        private InnerClass() {
        }

        public void innerSubMethod() {
            // Java: 要调用某一个类的实例方法，一定要持有一个方法所在类的实例
            outerMethod(); // => this.this$0.outerMethod();
            System.out.println("this is innerSubMethod");
        }
    }

    /**
     * 如果一个类存在内部类的情况下，内部类想要访问外部类的私有方法或属性，外部类想要访问内部类的私有方法或者属性时，编译器会自动生成一些方法来达到上述访问目的
     */
    @Test
    public void fieldDemo() {
        InnerClass innerClass = new InnerClass();
        System.out.println("innerClass.subField1 = " + innerClass.subField1);
        System.out.println("innerClass.subField2 = " + innerClass.subField2);
        /**
         * Class name: com.star.Synthetic$InnerClass - access$100 : true
         * Class name: com.star.Synthetic$InnerClass - access$200 : true
         * Class name: com.star.Synthetic$InnerClass - getSubField2 : false
         * Class name: com.star.Synthetic$InnerClass - innerSubMethod : false
         * Class name: com.star.Synthetic$InnerClass - setSubField1 : false
         * Class name: com.star.Synthetic$InnerClass - getSubField1 : false
         * Class name: com.star.Synthetic$InnerClass - setSubField2 : false
         */
        for (Method method : innerClass.getClass().getDeclaredMethods()) {
            System.out.println(
                    "Class name: " + innerClass.getClass().getName()
                            + " - " + method.getName() + " : " + method.isSynthetic()
            );
        }

        /**
         * subField1 false
         * subField2 false
         * this$0 true
         */
        for (Field field : innerClass.getClass().getDeclaredFields()) {
            System.out.println(field.getName() + " " + field.isSynthetic());
        }

        /**
         * com.star.Synthetic$InnerClass true
         * 4096 => synthetic
         *
         * com.star.Synthetic$InnerClass false
         * 2
         * private
         */
        for (Constructor<?> constructor : innerClass.getClass().getDeclaredConstructors()) {
            System.out.println(constructor.getName() + " " + constructor.isSynthetic());
            System.out.println(constructor.getModifiers());
            System.out.println(Modifier.toString(constructor.getModifiers()));
        }
    }

}
