package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Agencia extends Agent {

	//poderacion calificacion y precio
	Float pesoCalif, pesoPrecio;

	protected void setup(){
		System.out.println("Agencia "+getAID().getName()+" esta lista.");
		Object[] args = getArguments();

		//se supone que viene le peso de la calificacion y el peso del precio
		if (args != null && args.length > 0) {
			pesoCalif = Float.parseFloat((String) args[0]);
			pesoPrecio = Float.parseFloat((String) args[1]);
		}

		// Registrando el servicio de agencia
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("agencia");
		sd.setName("JADE-agencia");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}


		//agregando comportamiento para servir requerimientos
		addBehaviour(new ServidorDeRequerimientos());
	}

	protected void takeDown() {
		// desregistrando el servico
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
	}


	private class ServidorDeRequerimientos extends Behaviour {

		//variable que controla el flujo de los comporotamientos
		private int step = 0;

		//template para filtrar los mensajes
		private MessageTemplate mt;

		//Mensaje del turista
		private ACLMessage mensajeDeTurista = new ACLMessage();

		//------------LUGARES------------------
		//lugares con los que negocia
		private AID[] lugares;

		// Agente que ofrece el mejor lugar hasta ahora
		private AID mejorLugar; 

		// La mejor oferta de lugar hasta ahora
		private String[] mejorOfertaLugar; 

		// Cantidad de lugares que no quieren negociar mas
		private int cantRefuseLugar = 0;

		//requerimiento actual lugar
		private String[] requerimientoActualLugar;

	
		
		//------------Transportes
		//lugares con los que negocia
		private AID[] transportes;

		// Agente que ofrece el mejor lugar hasta ahora
		private AID mejorTransporte; 

		// La mejor oferta de lugar hasta ahora
		private String[] mejorOfertaTransporte; 

		// Cantidad de lugares que no quieren negociar mas
		private int cantRefuseTransporte = 0;

		//requerimiento actual lugar
		private String[] requerimientoActualTransporte;
		

		public void action() {

			switch (step){
			case 0:
				//obtener el mensaje cfp de los turistas y procesarlos
				mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
				ACLMessage msg = myAgent.receive(mt);

				if (msg != null) {

					//se usa despues para crear la respuesta
					mensajeDeTurista = msg;

					//supone que viene ciudad@tipo@categoria@cantdias@preciomaxporpersona---ciudad@tipo@categoria@cantpersonas@preciomaxporpersona
					String requerimiento = msg.getContent();
					requerimientoActualLugar = requerimiento.split("---")[0].split("@");
					requerimientoActualTransporte = requerimiento.split("---")[1].split("@");

					//inicia la negociacion. 
					// buscar los lugares
					DFAgentDescription templateLugar = new DFAgentDescription();
					ServiceDescription sdLugar = new ServiceDescription();
					String tipoLugar = "alquiler-"+requerimientoActualLugar[0]+"-"+getAID().getLocalName();
					sdLugar.setType(tipoLugar);
					templateLugar.addServices(sdLugar);
					try {
						DFAgentDescription[] resultLugar = DFService.search(myAgent, templateLugar); 
						lugares = new AID[resultLugar.length];
						for (int i = 0; i < resultLugar.length; ++i) {
							lugares[i] = resultLugar[i].getName();

						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// buscar los Transportes
					DFAgentDescription templateTransporte = new DFAgentDescription();
					ServiceDescription sdTransporte = new ServiceDescription();
					String tipoTransporte = "transporte-"+requerimientoActualTransporte[0]+"-"+getAID().getLocalName();
					sdTransporte.setType(tipoTransporte);
					templateTransporte.addServices(sdTransporte);
					try {
						DFAgentDescription[] resultTransporte = DFService.search(myAgent, templateTransporte); 
						transportes = new AID[resultTransporte.length];
						for (int i = 0; i < resultTransporte.length; ++i) {
							transportes[i] = resultTransporte[i].getName();

						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					
					if (lugares.length == 0 || transportes.length == 0){
						//se setea el paso 4 (no se encontaron lugares) y se rompe el case
						step = 9;
						break;
					}
					//---------------- desde aca es propio de lugar hasta el paso 4
					//enviar los cfp a los lugares
					ACLMessage cfpLugar = new ACLMessage(ACLMessage.CFP);
					for (AID elem : lugares) {
						cfpLugar.addReceiver(elem);
					} 

					String contenido = requerimientoActualLugar[1]+"@"+requerimientoActualLugar[2];
					cfpLugar.setContent(contenido);
					cfpLugar.setConversationId("lugar-trade");
					cfpLugar.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
					myAgent.send(cfpLugar);

					//Preparar el template para recibir los propose/refuse
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("lugar-trade"), 
							MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
									MessageTemplate.MatchPerformative(ACLMessage.REFUSE)));

					step = 1;
				} else {
					block();
				}
				break;
			case 1:
				// Recibe Propose/Refuse de los lugares
				//Aqui ocurre la magia
				ACLMessage respuestaLugar = myAgent.receive(mt);
				if (respuestaLugar != null) {
					// respuesta recibida
					if (respuestaLugar.getPerformative() == ACLMessage.PROPOSE) {
						// se trata de una oferta 
						String[] propuestaLugar =respuestaLugar.getContent().split("@");
						Float valorOferta = this.calcularValorOfertaLugar(propuestaLugar);

						if (mejorLugar == null){
							// es la primer oferta, la guardamos como mejor
							mejorOfertaLugar = propuestaLugar;
							mejorLugar = respuestaLugar.getSender();

						} else if (valorOferta>(this.calcularValorOfertaLugar(mejorOfertaLugar))){
							//obtuvo una calificacion mayor que la actual
							this.actualizarMejorOfertaLugar(respuestaLugar);
						} else if (valorOferta.equals(this.calcularValorOfertaLugar(mejorOfertaLugar))) {
							//obtuvieron igual calificacion, hay que ver cual tiene el mejor precio
							if (Float.parseFloat(mejorOfertaLugar[0]) > Float.parseFloat(propuestaLugar[0])){
								this.actualizarMejorOfertaLugar(respuestaLugar);
							} else  {
								//no es una mejor oferta, se debe enviar el Reject
								this.enviarRefuseLugar(respuestaLugar);
							}
						} else {
							//no es una mejor oferta, se debe enviar el reject
							this.enviarRefuseLugar(respuestaLugar);
						}

					} else if (respuestaLugar.getPerformative() == ACLMessage.REFUSE){
						cantRefuseLugar++;
					}
					if ((cantRefuseLugar == lugares.length - 1) | (cantRefuseLugar == lugares.length) ) {
						// todos contestaron REFUSE, menos uno, el que dio la mejor oferta
						// o bien todos contestaron con un refuse (nadie puede dar una oferta)
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// Se envia el acept al lugar
				// Si no hubiera el comportamiento termina por la definicion de done()
				
				ACLMessage orderLugar = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				orderLugar.addReceiver(mejorLugar);
				orderLugar.setContent(mejorOfertaLugar[0] +"@"+mejorOfertaLugar[1]+"@"+mejorOfertaLugar[2]);
				orderLugar.setConversationId("lugar-trade");
				orderLugar.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(orderLugar);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("lugar-trade"),
						MessageTemplate.MatchInReplyTo(orderLugar.getReplyWith()));
				step = 3;
				break;
			case 3:      
				// Recibe el gracias
				respuestaLugar = myAgent.receive(mt);
				if (respuestaLugar != null) {
					// se recibe el gracias
					if (respuestaLugar.getPerformative() == ACLMessage.INFORM) {
						System.out.println("Lugar elegido, agente: "+respuestaLugar.getSender().getName());
					}

					step = 4;
				}
				else {
					block();
				}
				break;
			case 4:
				//---------------- desde aca es propio de transporte

				//enviar los cfp a los transportes
				ACLMessage cfpTransporte = new ACLMessage(ACLMessage.CFP);
				for (AID elem : transportes) {
					cfpTransporte.addReceiver(elem);
				} 

				String contenidoTransporte = requerimientoActualTransporte[1]+"@"+requerimientoActualTransporte[2];
				cfpTransporte.setContent(contenidoTransporte);
				cfpTransporte.setConversationId("Transporte-trade");
				cfpTransporte.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfpTransporte);

				//Preparar el template para recibir los propose/refuse
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("transporte-trade"), 
						MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
								MessageTemplate.MatchPerformative(ACLMessage.REFUSE)));

				step = 5;
				break;
			
			case 5:
				// Recibe Propose/Refuse de los transportes
				//Aqui ocurre la magia
				ACLMessage respuestaTransporte = myAgent.receive(mt);
				if (respuestaTransporte != null) {
					// respuesta recibida
					if (respuestaTransporte.getPerformative() == ACLMessage.PROPOSE) {
						// se trata de una oferta 
						String[] propuestaTransporte =respuestaTransporte.getContent().split("@");
						Float valorOfertaTransporte = this.calcularValorOfertaTransporte(propuestaTransporte);

						if (mejorTransporte == null){
							// es la primer oferta, la guardamos como mejor
							mejorOfertaTransporte = propuestaTransporte;
							mejorTransporte = respuestaTransporte.getSender();

						} else if (valorOfertaTransporte>(this.calcularValorOfertaTransporte(mejorOfertaTransporte))){
							//obtuvo una calificacion mayor que la actual
							this.actualizarMejorOfertaTransporte(respuestaTransporte);
						} else if (valorOfertaTransporte.equals(this.calcularValorOfertaTransporte(mejorOfertaTransporte))) {
							//obtuvieron igual calificacion, hay que ver cual tiene el mejor precio
							if (Float.parseFloat(mejorOfertaTransporte[0]) > Float.parseFloat(propuestaTransporte[0])){
								this.actualizarMejorOfertaTransporte(respuestaTransporte);
							} else  {
								//no es una mejor oferta, se debe enviar el Reject
								this.enviarRefuseTransporte(respuestaTransporte);
							}
						} else {
							//no es una mejor oferta, se debe enviar el reject
							this.enviarRefuseTransporte(respuestaTransporte);
						}

					} else if (respuestaTransporte.getPerformative() == ACLMessage.REFUSE){
						cantRefuseTransporte++;
					}
					if ((cantRefuseTransporte == transportes.length - 1) | (cantRefuseTransporte == transportes.length) ) {
						// todos contestaron REFUSE, menos uno, el que dio la mejor oferta
						// o bien todos contestaron con un refuse (nadie puede dar una oferta)
						step = 6; 
					}
				}
				else {
					block();
				}
				break;
				
			case 6:
				// Se envia el acept al transporte
				// Si no hubiera el comportamiento termina por la definicion de done()

				ACLMessage orderTransporte = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				orderTransporte.addReceiver(mejorTransporte);
				orderTransporte.setContent(mejorOfertaTransporte[0] +"@"+mejorOfertaTransporte[1]+"@"+mejorOfertaTransporte[2]);
				orderTransporte.setConversationId("transporte-trade");
				orderTransporte.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(orderTransporte);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("transporte-trade"),
						MessageTemplate.MatchInReplyTo(orderTransporte.getReplyWith()));
				step = 7;
				break;
				
			case 7:
				// Recibe el gracias
				respuestaTransporte = myAgent.receive(mt);
				if (respuestaTransporte != null) {
					// se recibe el gracias
					if (respuestaTransporte.getPerformative() == ACLMessage.INFORM) {
						System.out.println("Transporte elegido, agente: "+respuestaTransporte.getSender().getName());
					}

					step = 8;
				}
				else {
					block();
				}
				break;
			}	
		}


		//metodos de soporte para negociacion con lugar
		private float calcularValorOfertaLugar(String[] propuesta){

			Float precio = Float.parseFloat(propuesta[0]);
			Integer calif = Integer.parseInt(propuesta[2]);
			int precioCalif = this.calcularValorPrecioLugar(precio);

			return pesoCalif*(calif) + pesoPrecio*(precioCalif);

		}

		private int calcularValorPrecioLugar(Float precio){
			Float porcDif = (precio * 100) / (Float.parseFloat(requerimientoActualLugar[4]));

			if (porcDif>100){
				return -100;
			}else if (porcDif==100){
				return 1;
			} else if (porcDif>=95){
				return 2;
			}else if (porcDif>=90){
				return 3;
			}else if (porcDif>=85){
				return 4;
			}else{
				return 5;
			}
		}
		private float calcularPrecioASuperarLugar(String[] ofertaActual){

			if (ofertaActual[2].equals(this.mejorOfertaLugar[2])){
				//tienen la misma categoria debe superar el precio actual
				return Float.parseFloat(this.mejorOfertaLugar[0]);
			}

			float  valorMejorOferta  = calcularValorOfertaLugar(this.mejorOfertaLugar);
			int califPrecioASuperar = (int) Math.ceil((valorMejorOferta - pesoCalif*Integer.parseInt(ofertaActual[2]))/pesoPrecio);
			
			System.out.println("Valor mejor oferta:" + valorMejorOferta);
			System.out.println("Peso calif:" + pesoCalif);
			System.out.println("Valor mejor oferta:" + valorMejorOferta);


			System.out.println("precio a superar:" + califPrecioASuperar);

			if (califPrecioASuperar <= 5){
				//no tienen la misma, se devuelve un valor segun la categoria caclulada
				switch (califPrecioASuperar){
				case 1: return Float.parseFloat(requerimientoActualLugar[4]);
				case 2: return (float) (Float.parseFloat(requerimientoActualLugar[4]) * 0.95);
				case 3: return (float) (Float.parseFloat(requerimientoActualLugar[4]) * 0.90);
				case 4: return (float) (Float.parseFloat(requerimientoActualLugar[4]) * 0.85);
				default: return (float) (Float.parseFloat(requerimientoActualLugar[4]) * 0.84);

				} 
			} else {
				//se devuelve un valor negativo de precio a superar para forzar el refuse ya que nunca va a poder obtener una calificación mayor a 5.
				return -100;
			}

		}

		private void actualizarMejorOfertaLugar (ACLMessage respuesta){

			//se le debe enviar reject al que estaba antes como mejor y cambiar al nuevo
			ACLMessage rejectLugar = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
			rejectLugar.setConversationId("lugar-trade");
			rejectLugar.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
			//agrego al receptor al que antes era mejor lugar
			rejectLugar.addReceiver(mejorLugar);
			//actualizo el mejor lugar
			mejorLugar = respuesta.getSender();

			//alamaceno al oferta del que era el mejor lugar
			String [] mejorAnterior = mejorOfertaLugar;
			//Actualizo la mejor oferta
			mejorOfertaLugar = respuesta.getContent().split("@");
			//Creo el contenido del reject con la oferta anterior
			rejectLugar.setContent(calcularPrecioASuperarLugar(mejorAnterior)+"@"+requerimientoActualLugar[3]);

			myAgent.send(rejectLugar);
		}

		private void enviarRefuseLugar(ACLMessage respuesta){
			ACLMessage rejectLugar = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
			rejectLugar.setConversationId("lugar-trade");
			rejectLugar.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
			rejectLugar.addReceiver(respuesta.getSender());
			rejectLugar.setContent(calcularPrecioASuperarLugar(respuesta.getContent().split("@"))+"@"+requerimientoActualLugar[3]);

			myAgent.send(rejectLugar);
		}

		//------------------------------------------------------------
		// metodos de soporte para negociacion con transporte
		private float calcularValorOfertaTransporte(String[] propuesta){

			Float precio = Float.parseFloat(propuesta[0]);
			Integer calif = Integer.parseInt(propuesta[2]);
			int precioCalif = this.calcularValorPrecioTransporte(precio);

			return pesoCalif*(calif) + pesoPrecio*(precioCalif);

		}

		private int calcularValorPrecioTransporte(Float precio){
			Float porcDif = (precio * 100) / (Float.parseFloat(requerimientoActualTransporte[4]));

			if (porcDif>100){
				return -100;
			}else if (porcDif==100){
				return 1;
			} else if (porcDif>=95){
				return 2;
			}else if (porcDif>=90){
				return 3;
			}else if (porcDif>=85){
				return 4;
			}else{
				return 5;
			}
		}
		private float calcularPrecioASuperarTransporte(String[] ofertaActual){

			if (ofertaActual[2].equals(this.mejorOfertaTransporte[2])){
				//tienen la misma categoria debe superar el precio actual
				return Float.parseFloat(this.mejorOfertaTransporte[0]);
			}

			float  valorMejorOferta  = calcularValorOfertaTransporte(this.mejorOfertaTransporte);
			int califPrecioASuperar = (int) Math.ceil((valorMejorOferta - pesoCalif*Integer.parseInt(ofertaActual[2]))/0.8);


			if (califPrecioASuperar <= 5){
				//no tienen la misma, se devuelve un valor segun la categoria caclulada
				switch (califPrecioASuperar){
				case 1: return Float.parseFloat(requerimientoActualTransporte[4]);
				case 2: return (float) (Float.parseFloat(requerimientoActualTransporte[4]) * 0.95);
				case 3: return (float) (Float.parseFloat(requerimientoActualTransporte[4]) * 0.90);
				case 4: return (float) (Float.parseFloat(requerimientoActualTransporte[4]) * 0.85);
				default: return (float) (Float.parseFloat(requerimientoActualTransporte[4]) * 0.84);

				} 
			} else {
				//se devuelve un valor negativo de precio a superar para forzar el refuse ya que nunca va a poder obtener una calificación mayor a 5.
				return -100;
			}

		}

		private void actualizarMejorOfertaTransporte (ACLMessage respuesta){

			//se le debe enviar reject al que estaba antes como mejor y cambiar al nuevo
			ACLMessage rejectTransporte = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
			rejectTransporte.setConversationId("transporte-trade");
			rejectTransporte.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
			//agrego al receptor al que antes era mejor transport
			rejectTransporte.addReceiver(mejorTransporte);
			//actualizo el mejor transporte
			mejorTransporte = respuesta.getSender();

			//alamaceno al oferta del que era el mejor transporte
			String [] mejorAnterior = mejorOfertaTransporte;
			//Actualizo la mejor oferta
			mejorOfertaTransporte = respuesta.getContent().split("@");
			//Creo el contenido del reject con la oferta anterior
			rejectTransporte.setContent(calcularPrecioASuperarTransporte(mejorAnterior)+"@"+requerimientoActualTransporte[3]);

			myAgent.send(rejectTransporte);
		}

		private void enviarRefuseTransporte(ACLMessage respuesta){
			ACLMessage rejectTransporte = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
			rejectTransporte.setConversationId("transporte-trade");
			rejectTransporte.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
			rejectTransporte.addReceiver(respuesta.getSender());
			rejectTransporte.setContent(calcularPrecioASuperarTransporte(respuesta.getContent().split("@"))+"@"+requerimientoActualTransporte[3]);

			myAgent.send(rejectTransporte);
		}

		public boolean done() {
			ACLMessage respuesta = mensajeDeTurista.createReply();
			if ((step==2 && mejorLugar == null) || (step==6 && mejorTransporte == null)) {
				respuesta.setPerformative(ACLMessage.REFUSE);
				respuesta.setContent("no-hay-ofertas-disponibles");

			}
			else if (step==9){
				respuesta.setPerformative(ACLMessage.REFUSE);
				respuesta.setContent("agencia-sin-contactos");
			}
			else if (step==8){
				respuesta.setPerformative(ACLMessage.PROPOSE);

				//se envia precio@tipo@categoria@idlocaldellugar
				String contenidoLugar = mejorOfertaLugar[0]+"@"+mejorOfertaLugar[1]+"@"+mejorOfertaLugar[2]+"@"+mejorLugar.getLocalName();
				String contenidoTransporte = mejorOfertaTransporte[0]+"@"+mejorOfertaTransporte[1]+"@"+mejorOfertaTransporte[2]+"@"+mejorTransporte.getLocalName();
				respuesta.setContent(contenidoLugar+"---"+contenidoTransporte);
			}


			if (((step==2 && mejorLugar == null) || (step==6 && mejorTransporte == null)) || (step == 8) || (step == 9)){

				//enviando mensaje al turista
				myAgent.send(respuesta);
				//reiniciando variables para la proxima ejecucion del comportamiento

				step = 0;
				lugares = null;
				mejorLugar = null;
				mejorOfertaLugar = null;
				cantRefuseLugar = 0;
				requerimientoActualLugar = null;
				mt = null;
				
				transportes = null;
				mejorTransporte = null; 
				mejorOfertaTransporte = null; 
				cantRefuseTransporte = 0;
				requerimientoActualTransporte = null;
				
			}

			return (((step==2 && mejorLugar == null) || (step==6 && mejorTransporte == null)) || (step == 8) || (step == 9));
		}
	}
}



