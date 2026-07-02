package com.google.firebase.components;

import com.google.firebase.events.Event;
import com.google.firebase.events.Publisher;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
/* loaded from: classes.dex */
final class RestrictedComponentContainer implements ComponentContainer {
    private final Set<Qualified<?>> allowedDeferredInterfaces;
    private final Set<Qualified<?>> allowedDirectInterfaces;
    private final Set<Qualified<?>> allowedProviderInterfaces;
    private final Set<Class<?>> allowedPublishedEvents;
    private final Set<Qualified<?>> allowedSetDirectInterfaces;
    private final Set<Qualified<?>> allowedSetProviderInterfaces;
    private final ComponentContainer delegateContainer;

    @Override // com.google.firebase.components.ComponentContainer
    public /* synthetic */ Set setOf(Class cls) {
        Set of;
        of = setOf(Qualified.unqualified(cls));
        return of;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public RestrictedComponentContainer(Component<?> component, ComponentContainer container) {
        Set<Qualified<?>> directInterfaces = new HashSet<>();
        Set<Qualified<?>> providerInterfaces = new HashSet<>();
        Set<Qualified<?>> deferredInterfaces = new HashSet<>();
        Set<Qualified<?>> setDirectInterfaces = new HashSet<>();
        Set<Qualified<?>> setProviderInterfaces = new HashSet<>();
        for (Dependency dependency : component.getDependencies()) {
            if (dependency.isDirectInjection()) {
                if (dependency.isSet()) {
                    setDirectInterfaces.add(dependency.getInterface());
                } else {
                    directInterfaces.add(dependency.getInterface());
                }
            } else if (dependency.isDeferred()) {
                deferredInterfaces.add(dependency.getInterface());
            } else if (dependency.isSet()) {
                setProviderInterfaces.add(dependency.getInterface());
            } else {
                providerInterfaces.add(dependency.getInterface());
            }
        }
        if (!component.getPublishedEvents().isEmpty()) {
            directInterfaces.add(Qualified.unqualified(Publisher.class));
        }
        this.allowedDirectInterfaces = Collections.unmodifiableSet(directInterfaces);
        this.allowedProviderInterfaces = Collections.unmodifiableSet(providerInterfaces);
        this.allowedDeferredInterfaces = Collections.unmodifiableSet(deferredInterfaces);
        this.allowedSetDirectInterfaces = Collections.unmodifiableSet(setDirectInterfaces);
        this.allowedSetProviderInterfaces = Collections.unmodifiableSet(setProviderInterfaces);
        this.allowedPublishedEvents = component.getPublishedEvents();
        this.delegateContainer = container;
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> T get(Class<T> anInterface) {
        if (!this.allowedDirectInterfaces.contains(Qualified.unqualified(anInterface))) {
            throw new DependencyException(String.format("Attempting to request an undeclared dependency %s.", anInterface));
        }
        T value = (T) this.delegateContainer.get(anInterface);
        if (!anInterface.equals(Publisher.class)) {
            return value;
        }
        T publisher = (T) new RestrictedPublisher(this.allowedPublishedEvents, (Publisher) value);
        return publisher;
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> T get(Qualified<T> anInterface) {
        if (!this.allowedDirectInterfaces.contains(anInterface)) {
            throw new DependencyException(String.format("Attempting to request an undeclared dependency %s.", anInterface));
        }
        return (T) this.delegateContainer.get(anInterface);
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> Provider<T> getProvider(Class<T> anInterface) {
        return getProvider(Qualified.unqualified(anInterface));
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> Deferred<T> getDeferred(Class<T> anInterface) {
        return getDeferred(Qualified.unqualified(anInterface));
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> Provider<Set<T>> setOfProvider(Class<T> anInterface) {
        return setOfProvider(Qualified.unqualified(anInterface));
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> Provider<T> getProvider(Qualified<T> anInterface) {
        if (!this.allowedProviderInterfaces.contains(anInterface)) {
            throw new DependencyException(String.format("Attempting to request an undeclared dependency Provider<%s>.", anInterface));
        }
        return this.delegateContainer.getProvider(anInterface);
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> Deferred<T> getDeferred(Qualified<T> anInterface) {
        if (!this.allowedDeferredInterfaces.contains(anInterface)) {
            throw new DependencyException(String.format("Attempting to request an undeclared dependency Deferred<%s>.", anInterface));
        }
        return this.delegateContainer.getDeferred(anInterface);
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> Provider<Set<T>> setOfProvider(Qualified<T> anInterface) {
        if (!this.allowedSetProviderInterfaces.contains(anInterface)) {
            throw new DependencyException(String.format("Attempting to request an undeclared dependency Provider<Set<%s>>.", anInterface));
        }
        return this.delegateContainer.setOfProvider(anInterface);
    }

    @Override // com.google.firebase.components.ComponentContainer
    public <T> Set<T> setOf(Qualified<T> anInterface) {
        if (!this.allowedSetDirectInterfaces.contains(anInterface)) {
            throw new DependencyException(String.format("Attempting to request an undeclared dependency Set<%s>.", anInterface));
        }
        return this.delegateContainer.setOf(anInterface);
    }

    /* loaded from: classes.dex */
    private static class RestrictedPublisher implements Publisher {
        private final Set<Class<?>> allowedPublishedEvents;
        private final Publisher delegate;

        public RestrictedPublisher(Set<Class<?>> allowedPublishedEvents, Publisher delegate) {
            this.allowedPublishedEvents = allowedPublishedEvents;
            this.delegate = delegate;
        }

        @Override // com.google.firebase.events.Publisher
        public void publish(Event<?> event) {
            if (!this.allowedPublishedEvents.contains(event.getType())) {
                throw new DependencyException(String.format("Attempting to publish an undeclared event %s.", event));
            }
            this.delegate.publish(event);
        }
    }
}
