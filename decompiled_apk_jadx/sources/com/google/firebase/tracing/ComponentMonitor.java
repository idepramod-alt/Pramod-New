package com.google.firebase.tracing;

import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentFactory;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRegistrarProcessor;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes.dex */
public class ComponentMonitor implements ComponentRegistrarProcessor {
    @Override // com.google.firebase.components.ComponentRegistrarProcessor
    public List<Component<?>> processRegistrar(ComponentRegistrar registrar) {
        List<Component<?>> components = new ArrayList<>();
        for (final Component comp : registrar.getComponents()) {
            final String name = comp.getName();
            if (name != null) {
                comp = comp.withFactory(new ComponentFactory() { // from class: com.google.firebase.tracing.ComponentMonitor$$ExternalSyntheticLambda0
                    @Override // com.google.firebase.components.ComponentFactory
                    public final Object create(ComponentContainer componentContainer) {
                        return ComponentMonitor.lambda$processRegistrar$0(name, comp, componentContainer);
                    }
                });
            }
            components.add(comp);
        }
        return components;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ Object lambda$processRegistrar$0(String name, Component old, ComponentContainer c) {
        try {
            FirebaseTrace.pushTrace(name);
            return old.getFactory().create(c);
        } finally {
            FirebaseTrace.popTrace();
        }
    }
}
