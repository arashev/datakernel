import Service from '../../common/Service';
import RoomsOTOperation from "./ot/RoomsOTOperation";
import {randomString, wait, getDialogRoomId} from '../../common/utils';

const RETRY_TIMEOUT = 1000;
const ROOM_ID_LENGTH = 32;

class RoomsService extends Service {
  constructor(roomsOTStateManager, pubicKey) {
    super({
      rooms: new Map(),
      roomsReady: false,
    });
    this._roomsOTStateManager = roomsOTStateManager;
    this._reconnectTimeout = null;
    this._myPublicKey = pubicKey;
  }

  static createFrom(roomsOTStateManager, pubKey) {
    return new RoomsService(roomsOTStateManager, pubKey);
  }

  async init() {
    try {
      await this._roomsOTStateManager.checkout();
    } catch (err) {
      console.log(err);
      await this._reconnectDelay();
      await this.init();
      return;
    }

    this._onStateChange();

    this._roomsOTStateManager.addChangeListener(this._onStateChange);
  }

  stop() {
    clearTimeout(this._reconnectTimeout);
    this._roomsOTStateManager.removeChangeListener(this._onStateChange);
  }

  async createRoom(participants) {
    const roomId = randomString(ROOM_ID_LENGTH);
    await this._createRoom(roomId, [...participants, this._myPublicKey]);
    return roomId;
  }

  async createDialog(participantPublicKey) {
    const participants = [this._myPublicKey, participantPublicKey];
    const roomId = getDialogRoomId(participants);

    let roomExists = false;
    [...this.state.rooms].map(([id, ]) => {
      if (id === roomId) {
        roomExists = true;
      }
    });
    if (roomExists) {
      return;
    }
    await this._createRoom(roomId, participants);
  }

  async quitRoom(roomId) {
    const room = this.state.rooms.get(roomId);
    if (!room) {
      return;
    }

    const deleteRoomOperation = new RoomsOTOperation(roomId, room.participants, true);
    this._roomsOTStateManager.add([deleteRoomOperation]);
    await this._sync();
  }

  async _createRoom(roomId, participants) {
    const addRoomOperation = new RoomsOTOperation(roomId, participants, false);
    this._roomsOTStateManager.add([addRoomOperation]);
    await this._sync();
  }

  _onStateChange = () => {
    this.setState({
      rooms: this._getRooms(),
      roomsReady: true
    });
  };

  _getRooms() {
    const rooms = [...this._roomsOTStateManager.getState()]
      .map(([roomId, room]) => (
        [roomId, {
          participants: room.participants,
          dialog: room.participants.length === 2 && roomId === getDialogRoomId(room.participants)
        }]
      ));
    return new Map(rooms);
  }

  _reconnectDelay() {
    return new Promise(resolve => {
      this._reconnectTimeout = setTimeout(resolve, RETRY_TIMEOUT);
    });
  }

  async _sync() {
    try {
      await this._roomsOTStateManager.sync();
    } catch (err) {
      console.log(err);
      await wait(RETRY_TIMEOUT);
      await this._sync();
    }
  }
}

export default RoomsService;
