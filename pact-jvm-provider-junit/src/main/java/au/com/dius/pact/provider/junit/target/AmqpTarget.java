package au.com.dius.pact.provider.junit.target;

import au.com.dius.pact.model.Interaction;
import au.com.dius.pact.model.Pact;
import au.com.dius.pact.provider.ConsumerInfo;
import au.com.dius.pact.provider.PactVerification;
import au.com.dius.pact.provider.ProviderInfo;
import au.com.dius.pact.provider.ProviderVerifier;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactFolder;
import au.com.dius.pact.provider.junit.loader.PactFolderLoader;
import org.codehaus.groovy.runtime.MethodClosure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Out-of-the-box implementation of {@link Target},
 * that run {@link Interaction} against message pact and verify response
 */
public class AmqpTarget extends BaseTarget {
  private static final Logger LOGGER = LoggerFactory.getLogger(AmqpTarget.class);
  private List<String> packagesToScan = Collections.emptyList();

  public AmqpTarget() { }

  /**
   * Initialises the AMPQ target with the list of packages to scan
   * @param packagesToScan List of JVM packages
   */
  public AmqpTarget(List<String> packagesToScan) {
    this.packagesToScan = packagesToScan;
  }

  /**
     * {@inheritDoc}
     */
    @Override
    public void testInteraction(final String consumerName, final Interaction interaction) {
      ProviderInfo provider = getProviderInfo();
      ConsumerInfo consumer = new ConsumerInfo(consumerName);
      ProviderVerifier verifier = setupVerifier(interaction, provider, consumer);

      Map<String, Object> failures = new HashMap<String, Object>();
      verifier.verifyResponseByInvokingProviderMethods(provider, consumer, interaction, interaction.getDescription(),
        failures);

      try {
        if (!failures.isEmpty()) {
          verifier.displayFailures(failures);
          throw getAssertionError(failures);
        }
      } finally {
        verifier.finialiseReports();
      }
    }

    ProviderVerifier setupVerifier(Interaction interaction, ProviderInfo provider,
                                         ConsumerInfo consumer) {
    ProviderVerifier verifier = new ProviderVerifier();
    verifier.setProjectClasspath(new MethodClosure(this, "getClassPathUrls"));

    setupReporters(verifier, provider.getName(), interaction.getDescription());

    verifier.initialiseReporters(provider);
    verifier.reportVerificationForConsumer(consumer, provider);

    if (interaction.getProviderState() != null) {
      verifier.reportStateForInteraction(interaction.getProviderState(), provider, consumer, true);
    }

    verifier.reportInteractionDescription(interaction);

    return verifier;
  }

  private URL[] getClassPathUrls() {
    return ((URLClassLoader)ClassLoader.getSystemClassLoader()).getURLs();
  }

  ProviderInfo getProviderInfo() {
    Provider provider = testClass.getAnnotation(Provider.class);
    ProviderInfo providerInfo = new ProviderInfo(provider.value());
    providerInfo.setVerificationType(PactVerification.ANNOTATED_METHOD);
    providerInfo.setPackagesToScan(packagesToScan);
    PactBroker annotation = testClass.getAnnotation(PactBroker.class);
    PactFolder folder = testClass.getAnnotation(PactFolder.class);
    if(annotation != null && annotation.host() != null) {
      List list = providerInfo.hasPactsFromPactBroker(annotation.protocol() + "://" + annotation.host() + (annotation.port() != null ? ":" + annotation.port() : ""));
      providerInfo.setConsumers(list);
    } else if (folder != null && folder.value() != null) {
      try {
        PactFolderLoader folderLoader = new PactFolderLoader(folder);
        Map<Pact, File> pactFileMap = folderLoader.loadPactsWithFiles(providerInfo.getName());
        List<ConsumerInfo> consumers = new ArrayList<ConsumerInfo>();
        for (Map.Entry<Pact, File> e: pactFileMap.entrySet()) {
          consumers.add(new ConsumerInfo(e.getKey().getConsumer().getName(), e.getValue()));
        }
        providerInfo.setConsumers(consumers);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return providerInfo;
  }
}
