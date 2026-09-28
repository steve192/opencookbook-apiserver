package com.sterul.opencookbookapiserver.services.legal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.util.function.SingletonSupplier;

import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;

/** What this server is built from and serves, credited as each license asks. */
@Service
public class OpenSourceComponentService {

    /** Written by the CycloneDX Maven plugin, configured by the Spring Boot parent. */
    static final String SERVER_BOM = "META-INF/sbom/application.cdx.json";
    /** Written by the admin panel's build, which ships inside this server. */
    static final String ADMIN_PANEL_BOM = "META-INF/sbom/admin-panel.cdx.json";

    private final CycloneDxBoms boms;
    private final JarLicenseFiles jarFiles;
    private final CatalogueDatasetReader catalogue;
    private final SingletonSupplier<List<OpenSourceComponent>> server =
            SingletonSupplier.of(this::readServerComponents);

    public OpenSourceComponentService(CycloneDxBoms boms, JarLicenseFiles jarFiles, CatalogueDatasetReader catalogue) {
        this.boms = boms;
        this.jarFiles = jarFiles;
        this.catalogue = catalogue;
    }

    /** Read once: what a running server ships does not change. */
    public List<OpenSourceComponent> serverComponents() {
        return server.obtain();
    }

    /** The data the food catalogue is built from. */
    public List<OpenSourceComponent> catalogueSources() {
        return catalogue.manifest().attributions().stream()
                .map(source -> new OpenSourceComponent(source.name(), source.license(), source.licenseUrl(), "",
                        source.homepage(), source.text(), ""))
                .toList();
    }

    private List<OpenSourceComponent> readServerComponents() {
        var licenses = jarFiles.licenses();
        var notices = jarFiles.notices();
        var byName = new LinkedHashMap<String, OpenSourceComponent>();
        Stream.of(SERVER_BOM, ADMIN_PANEL_BOM).flatMap(bom -> boms.read(bom).stream()).forEach(component ->
                byName.putIfAbsent(component.name(), new OpenSourceComponent(component.name(), component.license(),
                        component.licenseUrl(), component.author(), component.homepage(),
                        component.jarName() == null ? "" : notices.getOrDefault(component.jarName(), ""),
                        component.jarName() == null ? component.licenseText() :
                                licenses.getOrDefault(component.jarName(), component.licenseText()))));
        return byName.values().stream().sorted(Comparator.comparing(OpenSourceComponent::name)).toList();
    }
}
