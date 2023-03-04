package com.star.nbac;

import java.lang.reflect.Method;

/**
 * @author Starry
 * @since 03-04-2023
 */
public class Outer {

    /**
     * Outer
     *   Inner
     *
     * Inner => nestHost = Outer.class
     * Outer => nestMembers = {Inner.class}
     */

    public void outerPublic() throws Exception {
        new Inner().reflectOuter(new Outer());
    }

    private void outerPrivate() {

    }

    class Inner {

        public void innerPublic() {
            outerPrivate();
        }

        public void reflectOuter(Outer outer) throws Exception {
            // 反射调用报错
            Method outerPrivate = outer.getClass().getDeclaredMethod("outerPrivate");
            outerPrivate.invoke(outer);
        }

    }

}
