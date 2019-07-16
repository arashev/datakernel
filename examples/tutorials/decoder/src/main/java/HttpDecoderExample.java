import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import io.datakernel.async.Promise;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.di.annotation.Provides;
import io.datakernel.functional.Either;
import io.datakernel.http.AsyncServlet;
import io.datakernel.http.AsyncServletDecorator;
import io.datakernel.http.HttpResponse;
import io.datakernel.http.RoutingServlet;
import io.datakernel.http.decoder.DecodeErrors;
import io.datakernel.http.decoder.Decoder;
import io.datakernel.launcher.Launcher;
import io.datakernel.launchers.http.HttpServerLauncher;
import io.datakernel.writer.ByteBufWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.datakernel.http.HttpMethod.POST;
import static io.datakernel.http.decoder.Decoders.ofPost;
import static io.datakernel.util.CollectionUtils.map;

class Address {
	private final String title;

	public Address(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}

	@Override
	public String toString() {
		return "Address{title='" + title + '\'' + '}';
	}
}

class Contact {
	private final String name;
	private final Integer age;
	private final Address address;

	public Contact(String name, Integer age, Address address) {
		this.name = name;
		this.age = age;
		this.address = address;
	}

	public String getName() {
		return name;
	}

	public int getAge() {
		return age;
	}

	public Address getAddress() {
		return address;
	}

	@Override
	public String toString() {
		return "Contact{name='" + name + '\'' + ", age=" + age + ", address=" + address + '}';
	}
}

interface ContactDAO {
	List<Contact> list();

	void add(Contact user);
}

class ContactDAOImpl implements ContactDAO {
	private List<Contact> userList = new ArrayList<>();

	@Override
	public List<Contact> list() {
		return userList;
	}

	@Override
	public void add(Contact user) {
		userList.add(user);
	}
}

public final class HttpDecoderExample extends HttpServerLauncher {
	private final static String SEPARATOR = "-";

	//[START REGION_1]
	private final static Decoder<Address> ADDRESS_DECODER = Decoder.of(Address::new,
			ofPost("title", "")
					.validate(param -> !param.isEmpty(), "Title cannot be empty")
	);

	private final static Decoder<Contact> CONTACT_DECODER = Decoder.of(Contact::new,
			ofPost("name")
					.validate(name -> !name.isEmpty(), "Name cannot be empty"),
			ofPost("age")
					.map(Integer::valueOf, "Cannot parse age")
					.validate(age -> age >= 18, "Age must be greater than 18"),
			ADDRESS_DECODER.withId("contact-address")
	);
	//[END REGION_1]

	private static ByteBuf applyTemplate(Mustache mustache, Map<String, Object> scopes) {
		ByteBufWriter writer = new ByteBufWriter();
		mustache.execute(writer, scopes);
		return writer.getBuf();
	}

	@Provides
	ContactDAO dao() {
		return new ContactDAOImpl();
	}

	//[START REGION_2]
	@Provides
	AsyncServlet mainServlet(ContactDAO contactDAO) {
		Mustache contactListView = new DefaultMustacheFactory().compile("static/contactList.html");
		return RoutingServlet.create()
				.map("/", request -> Promise.of(
						HttpResponse.ok200()
								.withBody(applyTemplate(contactListView, map("contacts", contactDAO.list())))))
				.map(POST, "/add", AsyncServletDecorator.loadBody()
						.serve(request -> {
							Either<Contact, DecodeErrors> decodedUser = CONTACT_DECODER.decode(request);
							if (decodedUser.isLeft()) {
								contactDAO.add(decodedUser.getLeft());
							}
							Map<String, Object> scopes = map("contacts", contactDAO.list());
							if (decodedUser.isRight()) {
								scopes.put("errors", decodedUser.getRight().toMap(SEPARATOR));
							}
							return Promise.of(HttpResponse.ok200()
									.withBody(applyTemplate(contactListView, scopes)));
						}));
	}
	//[END REGION_2]

	public static void main(String[] args) throws Exception {
		Launcher launcher = new HttpDecoderExample();
		launcher.launch(args);
	}
}
