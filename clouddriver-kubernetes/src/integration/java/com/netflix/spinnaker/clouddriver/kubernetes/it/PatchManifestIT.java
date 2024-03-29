package com.netflix.spinnaker.clouddriver.kubernetes.it;

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PatchManifestIT extends BaseTest {
  private static final String DEPLOYMENT_1_NAME = "deployment1";
  private static final String MANIFEST_NAME = "deployment " + DEPLOYMENT_1_NAME;
  private static String account1Ns;

  @BeforeAll
  public static void setUpAll() throws IOException, InterruptedException {
    account1Ns = kubeCluster.createNamespace(ACCOUNT1_NAME);
  }

  @BeforeEach
  public void deployIfMissing() throws InterruptedException, IOException {
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        "patch-manifests",
        null,
        kubeCluster);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a patch manifest\n"
          + "  And a label to add\n"
          + "When sending patch manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then a pod is up and running a label is added\n===")
  @Test
  public void shouldPatchManifestFromText() throws IOException, InterruptedException {
    // ------------------------- when --------------------------
    Map<String, Object> patchManifest =
        KubeTestUtils.loadYaml("classpath:manifests/patch.yml").asMap();
    List<Map<String, Object>> patchBody =
        createPatchBody(patchManifest, Collections.emptyList(), Collections.emptyList());
    KubeTestUtils.deployAndWaitStable(baseUrl(), patchBody, account1Ns, MANIFEST_NAME);
    // ------------------------- then --------------------------
    podsAreReady();
    String labels =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.metadata.labels}'");
    assertTrue(
        labels.contains("\"testPatch\":\"success\""),
        "Expected patch to add label 'testPatch' with value 'success' to "
            + DEPLOYMENT_1_NAME
            + " deployment. Labels:\n"
            + labels);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a patch manifest without image tag\n"
          + "  And optional docker artifact present\n"
          + "When sending patch manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then the docker artifact is applied with the patch\n===")
  @Test
  public void shouldBindOptionalDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String imageNoTag = "index.docker.io/library/nginx";
    String imageWithTag = "index.docker.io/library/nginx:1.18";

    // ------------------------- when --------------------------
    Map<String, Object> patchManifest =
        KubeTestUtils.loadYaml("classpath:manifests/patch_container.yml")
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asMap();

    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        createPatchBody(
            patchManifest,
            Collections.singletonList(createArtifact(imageNoTag, imageWithTag)),
            Collections.emptyList()),
        account1Ns,
        MANIFEST_NAME);

    // ------------------------- then --------------------------
    podsAreReady();
    expectedImageIsDeployed(imageWithTag);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a patch manifest without image tag\n"
          + "  And required docker artifact present\n"
          + "When sending patch manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then the docker artifact is deployed\n===")
  @Test
  public void shouldBindRequiredDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String imageNoTag = "index.docker.io/library/nginx";
    String imageWithTag = "index.docker.io/library/nginx:1.18";

    // ------------------------- when --------------------------
    Map<String, Object> patchManifest =
        KubeTestUtils.loadYaml("classpath:manifests/patch_container.yml")
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asMap();
    List<Map<String, Object>> requiredArtifacts =
        Collections.singletonList(createArtifact(imageNoTag, imageWithTag));
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        createPatchBody(patchManifest, requiredArtifacts, requiredArtifacts),
        account1Ns,
        MANIFEST_NAME);

    // ------------------------- then --------------------------
    podsAreReady();
    expectedImageIsDeployed(imageWithTag);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a patch manifest without image tag\n"
          + "  And required docker artifact present\n"
          + "  And optional docker artifact present\n"
          + "When sending patch manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then required docker artifact is deployed\n===")
  @Test
  public void shouldBindRequiredOverOptionalDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String imageNoTag = "index.docker.io/library/nginx";
    String imageWithTag = "index.docker.io/library/nginx:1.18";
    String optionalImageWithTag = "index.docker.io/library/nginx:1.19";

    // ------------------------- and --------------------------
    Map<String, Object> artifact = createArtifact(imageNoTag, imageWithTag);
    Map<String, Object> optionalArtifact = createArtifact(imageNoTag, optionalImageWithTag);

    // ------------------------- when --------------------------
    Map<String, Object> patchManifest =
        KubeTestUtils.loadYaml("classpath:manifests/patch_container.yml")
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asMap();

    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        createPatchBody(
            patchManifest,
            Arrays.asList(optionalArtifact, artifact),
            Collections.singletonList(artifact)),
        account1Ns,
        MANIFEST_NAME);

    // ------------------------- then --------------------------
    podsAreReady();
    expectedImageIsDeployed(imageWithTag);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a CRD with \"ct\" shortName deployed outside of Spinnaker\n"
          + "When sending a patch shortName to \"cntb\" manifest operation\n"
          + "Then the CRD shortName is change to \"cntb\"\n===")
  @Test
  public void shouldPatchCrd() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    final String kind = "customResourceDefinition";
    final String crdName = "crontabs.stable.example.com";
    final String shortName = "cntb";
    Map<String, Object> crdManifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1.yml")
            .withValue("metadata.name", crdName)
            .withValue("spec.scope", "Namespaced")
            .withValue("spec.names.shortNames", new String[] {"ct"})
            .asMap();
    kubeCluster.execKubectl(" apply -f -", crdManifest);
    // ------------------------- when ---------------------------
    crdManifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1.yml")
            .withValue("metadata.name", crdName)
            .withValue("spec.scope", "Namespaced")
            .withValue("spec.names.shortNames", new String[] {shortName})
            .asMap();
    List<Map<String, Object>> request =
        KubeTestUtils.loadJson("classpath:requests/patch_manifest.json")
            .withValue("patchManifest.app", APP1_NAME)
            .withValue("patchManifest.manifestName", String.format("%s %s", kind, crdName))
            .withValue("patchManifest.patchBody", crdManifest)
            .withValue("patchManifest.allArtifacts", new Object[] {})
            .withValue("patchManifest.manifests", Arrays.asList(crdManifest))
            .withValue("patchManifest.trafficManagement", null)
            .withValue("patchManifest.moniker", null)
            .withValue("patchManifest.enableTraffic", null)
            .withValue("patchManifest.location", account1Ns)
            .withValue("patchManifest.account", ACCOUNT1_NAME)
            .withValue("patchManifest.skipExpressionEvaluation", null)
            .withValue("patchManifest.requiredArtifacts", new Object[] {})
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), request, account1Ns, String.format("%s %s", kind, crdName));
    // ------------------------- then ---------------------------
    String patched =
        kubeCluster.execKubectl(
            String.format("get %s %s -o jsonpath=\"{.spec.names.shortNames}\"", kind, crdName));
    assertTrue(patched.contains(shortName));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a CR with \"my-awesome-cron-image\" spec.image deployed outside of Spinnaker\n"
          + "When sending a patch spec.image to \"cron-image\" manifest operation\n"
          + "Then the CR spec.image is change to \"cron-image\"\n===")
  @Test
  public void shouldPatchCr() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    final String kind = "crontab.stable.example.com";
    final String crdName = "crontabs.stable.example.com";
    final String crName = "my-new-cron-object";
    final String crImage = "cron-image";
    final Map<String, Object> crdManifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1.yml")
            .withValue("metadata.name", crdName)
            .withValue("spec.scope", "Namespaced")
            .asMap();
    Map<String, Object> crManifest =
        KubeTestUtils.loadYaml("classpath:manifests/cr_v1.yml")
            .withValue("metadata.name", crName)
            .withValue("spec.image", "my-awesome-cron-image")
            .asMap();
    kubeCluster.execKubectl(" apply -f -", crdManifest);
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", crManifest);
    // ------------------------- when ---------------------------
    crManifest =
        KubeTestUtils.loadYaml("classpath:manifests/cr_v1.yml")
            .withValue("metadata.name", crName)
            .withValue("spec.image", crImage)
            .asMap();
    List<Map<String, Object>> request =
        KubeTestUtils.loadJson("classpath:requests/patch_manifest.json")
            .withValue("patchManifest.app", APP1_NAME)
            .withValue("patchManifest.manifestName", String.format("%s %s", kind, crName))
            .withValue("patchManifest.patchBody", crManifest)
            .withValue("patchManifest.allArtifacts", new Object[] {})
            .withValue("patchManifest.options.mergeStrategy", "merge")
            .withValue("patchManifest.manifests", Arrays.asList(crManifest))
            .withValue("patchManifest.trafficManagement", null)
            .withValue("patchManifest.moniker", null)
            .withValue("patchManifest.enableTraffic", null)
            .withValue("patchManifest.location", account1Ns)
            .withValue("patchManifest.account", ACCOUNT1_NAME)
            .withValue("patchManifest.skipExpressionEvaluation", null)
            .withValue("patchManifest.requiredArtifacts", new Object[] {})
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), request, account1Ns, String.format("%s %s", kind, crName));
    // ------------------------- then ---------------------------
    String patched =
        kubeCluster.execKubectl(
            String.format(
                "-n %s get %s %s -o jsonpath=\"{.spec.image}\"", account1Ns, kind, crName));
    assertTrue(patched.contains(crImage));
  }

  private List<Map<String, Object>> createPatchBody(
      Map<String, Object> patchManifest,
      List<Map<String, Object>> allArtifacts,
      List<Map<String, Object>> requiredArtifacts) {
    return KubeTestUtils.loadJson("classpath:requests/patch_manifest.json")
        .withValue("patchManifest.account", ACCOUNT1_NAME)
        .withValue("patchManifest.location", account1Ns)
        .withValue("patchManifest.manifestName", MANIFEST_NAME)
        .withValue("patchManifest.patchBody", patchManifest)
        .withValue("patchManifest.allArtifacts", allArtifacts)
        .withValue("patchManifest.requiredArtifacts", requiredArtifacts)
        .asList();
  }

  private Map<String, Object> createArtifact(String name, String imageWithTag) {
    return KubeTestUtils.loadJson("classpath:requests/artifact.json")
        .withValue("name", name)
        .withValue("type", "docker/image")
        .withValue("reference", imageWithTag)
        .withValue("version", imageWithTag.substring(name.length() + 1))
        .asMap();
  }

  private void podsAreReady() throws IOException, InterruptedException {
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
  }

  private void expectedImageIsDeployed(String expectedImageTag)
      throws IOException, InterruptedException {
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(
        expectedImageTag,
        imageDeployed,
        "Expected correct " + DEPLOYMENT_1_NAME + " image to be deployed");
  }
}
